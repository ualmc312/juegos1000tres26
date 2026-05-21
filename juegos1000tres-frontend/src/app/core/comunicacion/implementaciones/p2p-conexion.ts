import { Conexion } from '../conexion';
import { obtenerApiBaseUrl } from '../../config/api-base';

export interface P2PConexionOpciones {
  peerId?: string;
  signalingPeerId?: string;
  canalNombre?: string;
  role?: 'host' | 'player';
  signalingBaseUrl?: string;
  hostPeerId?: string;
  iceServers?: RTCIceServer[];
  onPeerConnected?: (peerId: string) => void;
  onPeerDisconnected?: (peerId: string) => void;
}

interface P2PSenalizacionMensaje {
  tipo: 'offer' | 'answer' | 'candidate';
  salaId: string;
  origenId: string;
  destinoId: string;
  payload: string;
  timestamp: number;
}

interface PeerState {
  peerId: string;
  peerConnection: RTCPeerConnection;
  dataChannel: RTCDataChannel | null;
  dataChannelAbierto: boolean;
  candidatosPendientes: RTCIceCandidateInit[];
  mensajesPendientes: string[];
}

/** Tiempo máximo (ms) que recibir() espera antes de rechazar con error de timeout */
const RECIBIR_TIMEOUT_MS = 5_000;

/** Intervalo de polling del bucle de señalización (ms) */
const INTERVALO_SENALIZACION_MS = 150;

/** Tiempo máximo (ms) que el player espera a que el DataChannel abra antes de reintentar la offer */
const OFFER_REINTENTO_TIMEOUT_MS = 8_000;

/** Máximo de reintentos de offer */
const MAX_REINTENTOS_OFFER = 5;

/**
 * Conexión P2P genérica basada en WebRTC.
 * No conoce el juego: solo transporta payloads entre peers de una misma sala.
 *
 * Arquitectura del handshake:
 *  - Player crea DataChannel + offer → backend (cola[hostPeerId])
 *  - Host recibe offer, crea answer → backend (cola[playerPeerId])
 *  - Ambos intercambian ICE candidates vía backend
 *  - DataChannel abre → ambos peers pueden comunicarse
 *
 * Robustez:
 *  - recibir() tiene timeout para no bloquear el bucleRecepcion del Traductor
 *  - El bucle de señalización consume TODOS los mensajes disponibles en cada tick
 *  - El player reintenta la offer si no hay DataChannel tras OFFER_REINTENTO_TIMEOUT_MS
 *  - Si connectionState === 'failed', el peer se cierra y el player reintenta
 *  - ICE servers múltiples (STUN públicos) para funcionar en redes distintas
 */
export class P2PConexion implements Conexion<string> {
  private readonly salaId: string;
  private readonly canalSala: string;
  private readonly peerId: string;
  private readonly signalingPeerId: string;
  private readonly role: 'host' | 'player';
  private readonly hostPeerId: string;
  private readonly signalingBaseUrl: string;
  private readonly iceServers: RTCIceServer[];
  private readonly onPeerConnected?: (peerId: string) => void;
  private readonly onPeerDisconnected?: (peerId: string) => void;

  private conectada = false;
  private colaMensajes: string[] = [];
  private esperas: Array<{ resolve: (valor: string) => void; timeoutId: ReturnType<typeof setTimeout> }> = [];
  private peers = new Map<string, PeerState>();
  private bucleSeñalizacionActiva = false;
  private mensajesBroadcastPendientes: string[] = [];

  constructor(salaId: string, canalSala: string, opciones: P2PConexionOpciones = {}) {
    if (!salaId || salaId.trim() === '') {
      throw new Error('El salaId es obligatorio');
    }
    if (!canalSala || canalSala.trim() === '') {
      throw new Error('El canalSala es obligatorio');
    }

    this.salaId = salaId.trim();
    this.canalSala = opciones.canalNombre?.trim() || canalSala.trim();
    this.peerId = opciones.peerId?.trim() || this.generarPeerId();
    this.role = opciones.role ?? 'player';
    this.hostPeerId = opciones.hostPeerId?.trim() || '';
    this.signalingPeerId = opciones.signalingPeerId?.trim() ||
      (this.role === 'host' ? (this.hostPeerId || this.peerId) : this.peerId);
    this.signalingBaseUrl = opciones.signalingBaseUrl?.trim() || obtenerApiBaseUrl();

    // Servidores STUN: Google + Cloudflare para funcionar en localhost y redes distintas
    this.iceServers = opciones.iceServers ?? [
      { urls: 'stun:stun.l.google.com:19302' },
      { urls: 'stun:stun1.l.google.com:19302' },
      { urls: 'stun:stun2.l.google.com:19302' },
      { urls: 'stun:stun.cloudflare.com:3478' },
    ];
    this.onPeerConnected = opciones.onPeerConnected;
    this.onPeerDisconnected = opciones.onPeerDisconnected;

    console.log(`[P2PConexion] Creada. role=${this.role} peerId=${this.peerId} signalingPeerId=${this.signalingPeerId} hostPeerId=${this.hostPeerId}`);
  }

  conectar(): void {
    if (this.conectada) {
      return;
    }

    if (typeof RTCPeerConnection === 'undefined') {
      throw new Error('RTCPeerConnection no está disponible en este entorno');
    }

    this.conectada = true;
    void this.iniciar();
  }

  desconectar(): void {
    this.conectada = false;
    this.bucleSeñalizacionActiva = false;

    for (const peer of this.peers.values()) {
      this.cerrarPeer(peer);
    }
    this.peers.clear();

    // Resolver todas las esperas pendientes para desbloquear al consumidor
    while (this.esperas.length > 0) {
      const entrada = this.esperas.shift();
      if (entrada) {
        clearTimeout(entrada.timeoutId);
        entrada.resolve(JSON.stringify({ error: 'Conexion P2P cerrada' }));
      }
    }

    this.colaMensajes = [];
  }

  enviar(payload: string): void {
    this.validarConexionActiva();

    if (!payload || payload.trim() === '') {
      throw new Error('El payload es obligatorio');
    }

    if (this.role === 'host') {
      this.mensajesBroadcastPendientes.push(payload);
      this.limitadorBroadcastPendientes();

      for (const peer of this.peers.values()) {
        this.enviarDatosPorPeer(peer, payload);
      }
      return;
    }

    const hostPeer = this.obtenerOCrearPeer(this.hostPeerId || 'host');
    this.enviarDatosPorPeer(hostPeer, payload);
  }

  /**
   * Recibe el siguiente payload disponible.
   * Si no hay mensajes en cola, espera hasta RECIBIR_TIMEOUT_MS.
   * Si se agota el tiempo, rechaza con error de timeout.
   * El bucleRecepcion del Traductor debe capturar este error y reintentar.
   */
  recibir(): string | Promise<string> {
    this.validarConexionActiva();

    if (this.colaMensajes.length > 0) {
      return this.colaMensajes.shift() as string;
    }

    return new Promise<string>((resolve, reject) => {
      const timeoutId = setTimeout(() => {
        const idx = this.esperas.findIndex(e => e.timeoutId === timeoutId);
        if (idx !== -1) {
          this.esperas.splice(idx, 1);
        }
        reject(new Error(`P2P recibir() timeout después de ${RECIBIR_TIMEOUT_MS}ms`));
      }, RECIBIR_TIMEOUT_MS);

      this.esperas.push({ resolve, timeoutId });
    });
  }

  getClasePayload(): { name: string } {
    return String;
  }

  getTipoComunicacion(): string {
    return 'WebRTC';
  }

  getSalaId(): string {
    return this.salaId;
  }

  getCanalSala(): string {
    return this.canalSala;
  }

  getTipoVerificacion(): string {
    return 'ComprobarHomologo';
  }

  comprobarHomologo(tipoComunicacionFront: string): boolean {
    if (!tipoComunicacionFront || tipoComunicacionFront.trim() === '') {
      return false;
    }
    return this.getTipoComunicacion().toLowerCase() === tipoComunicacionFront.toLowerCase().trim();
  }

  getPeerId(): string {
    return this.peerId;
  }

  getRole(): 'host' | 'player' {
    return this.role;
  }

  hayPeersConectados(): boolean {
    for (const peer of this.peers.values()) {
      if (peer.dataChannelAbierto) {
        return true;
      }
    }
    return false;
  }

  async esperarConectividadP2P(timeoutMs: number = 15000): Promise<void> {
    const inicio = Date.now();
    while (!this.hayPeersConectados() && this.conectada) {
      if (Date.now() - inicio > timeoutMs) {
        throw new Error(`Timeout esperando conectividad P2P después de ${timeoutMs}ms`);
      }
      await this.esperar(200);
    }

    if (!this.conectada) {
      throw new Error('Conexión P2P fue cerrada mientras se esperaba conectividad');
    }
  }

  private validarConexionActiva(): void {
    if (!this.conectada) {
      throw new Error('La conexion P2P debe estar activa');
    }
  }

  private async iniciar(): Promise<void> {
    this.bucleSeñalizacionActiva = true;

    // El bucle de señalización arranca PRIMERO en ambos roles para poder recibir mensajes
    void this.bucleSeñalizacion();

    if (this.role === 'player') {
      // Pequeña pausa para asegurar que el bucle ya está escuchando antes de enviar la offer
      await this.esperar(100);
      await this.enviarOfferAlHost();
      void this.watchdogReconexionPlayer();
    }
    // El host solo espera a recibir offers en el bucleSeñalizacion
  }

  /**
   * Crea o recrea el peer con el host y envía una offer.
   */
  private async enviarOfferAlHost(): Promise<void> {
    if (!this.hostPeerId || !this.conectada) {
      return;
    }

    // Limpiar peer previo fallido si existe
    const existente = this.peers.get(this.hostPeerId);
    if (existente) {
      this.cerrarPeer(existente);
      this.peers.delete(this.hostPeerId);
    }

    const enlace = this.obtenerOCrearPeer(this.hostPeerId);

    const canal = enlace.peerConnection.createDataChannel('reflejos');
    this.configurarCanalDatos(enlace, canal);

    const offer = await enlace.peerConnection.createOffer();
    await enlace.peerConnection.setLocalDescription(offer);

    console.log(`[P2PConexion] Player enviando offer → host ${this.hostPeerId}`);

    await this.enviarSenalizacion({
      tipo: 'offer',
      salaId: this.salaId,
      origenId: this.peerId,
      destinoId: this.hostPeerId,
      payload: JSON.stringify(enlace.peerConnection.localDescription),
      timestamp: Date.now(),
    });
  }

  /**
   * Watchdog del player: si pasado OFFER_REINTENTO_TIMEOUT_MS no hay DataChannel,
   * reenvía la offer. Máximo MAX_REINTENTOS_OFFER intentos.
   */
  private async watchdogReconexionPlayer(): Promise<void> {
    for (let intento = 1; intento <= MAX_REINTENTOS_OFFER; intento++) {
      await this.esperar(OFFER_REINTENTO_TIMEOUT_MS);

      if (!this.conectada || !this.bucleSeñalizacionActiva || this.hayPeersConectados()) {
        break;
      }

      console.warn(`[P2PConexion] Sin DataChannel tras ${OFFER_REINTENTO_TIMEOUT_MS}ms. Reintentando offer (${intento}/${MAX_REINTENTOS_OFFER})...`);

      try {
        await this.enviarOfferAlHost();
      } catch (err) {
        console.error('[P2PConexion] Error al reintentar offer:', err);
      }
    }
  }

  private generarPeerId(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return `peer-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
  }

  private obtenerOCrearPeer(peerId: string): PeerState {
    const clave = peerId.trim();
    const existente = this.peers.get(clave);
    if (existente) {
      return existente;
    }

    const peerConnection = new RTCPeerConnection({ iceServers: this.iceServers });
    const peer: PeerState = {
      peerId: clave,
      peerConnection,
      dataChannel: null,
      dataChannelAbierto: false,
      candidatosPendientes: [],
      mensajesPendientes: [],
    };

    peerConnection.onicecandidate = (evento) => {
      if (!evento.candidate || !this.conectada) {
        return;
      }
      void this.enviarSenalizacion({
        tipo: 'candidate',
        salaId: this.salaId,
        origenId: this.peerId,
        destinoId: peer.peerId,
        payload: JSON.stringify(evento.candidate.toJSON()),
        timestamp: Date.now(),
      });
    };

    peerConnection.onconnectionstatechange = () => {
      const state = peerConnection.connectionState;
      console.log(`[P2PConexion] Peer ${peer.peerId} connectionState → ${state}`);

      if (state === 'failed' || state === 'closed') {
        peer.dataChannelAbierto = false;
        this.cerrarPeer(peer);
        this.peers.delete(peer.peerId);

        if (this.role === 'host') {
          this.onPeerDisconnected?.(peer.peerId);
        }
        // Si somos player, el watchdog se encarga de reintentar la offer
      }
    };

    peerConnection.onicegatheringstatechange = () => {
      console.log(`[P2PConexion] Peer ${peer.peerId} iceGatheringState → ${peerConnection.iceGatheringState}`);
    };

    peerConnection.oniceconnectionstatechange = () => {
      console.log(`[P2PConexion] Peer ${peer.peerId} iceConnectionState → ${peerConnection.iceConnectionState}`);
    };

    peerConnection.ondatachannel = (evento) => {
      console.log(`[P2PConexion] ondatachannel recibido de peer ${peer.peerId}`);
      this.configurarCanalDatos(peer, evento.channel);
    };

    this.peers.set(clave, peer);
    return peer;
  }

  private configurarCanalDatos(peer: PeerState, canal: RTCDataChannel): void {
    peer.dataChannel = canal;

    canal.onopen = () => {
      console.log(`[P2PConexion] DataChannel ABIERTO con peer ${peer.peerId}`);
      peer.dataChannelAbierto = true;

      this.enviarMensajesPendientes(peer);

      if (this.role === 'host') {
        this.mensajesBroadcastPendientes.forEach((mensaje) => this.enviarAlCanal(canal, mensaje));
        this.onPeerConnected?.(peer.peerId);
      }
    };

    canal.onclose = () => {
      console.log(`[P2PConexion] DataChannel CERRADO con peer ${peer.peerId}`);
      peer.dataChannelAbierto = false;
      if (this.role === 'host') {
        this.onPeerDisconnected?.(peer.peerId);
      }
    };

    canal.onmessage = (evento) => {
      const payload = typeof evento.data === 'string' ? evento.data : '';
      if (!payload.trim()) {
        return;
      }

      if (this.esperas.length > 0) {
        const entrada = this.esperas.shift();
        if (entrada) {
          clearTimeout(entrada.timeoutId);
          entrada.resolve(payload);
        }
        return;
      }

      this.colaMensajes.push(payload);
    };
  }

  private enviarDatosPorPeer(peer: PeerState, payload: string): void {
    if (peer.dataChannelAbierto && peer.dataChannel && peer.dataChannel.readyState === 'open') {
      this.enviarAlCanal(peer.dataChannel, payload);
      return;
    }

    peer.mensajesPendientes.push(payload);
    this.limitadorColaPeer(peer);
  }

  private enviarAlCanal(canal: RTCDataChannel, payload: string): void {
    try {
      canal.send(payload);
    } catch {
      // Si el canal no está listo, el mensaje queda en cola del peer.
    }
  }

  private enviarMensajesPendientes(peer: PeerState): void {
    while (peer.mensajesPendientes.length > 0 && peer.dataChannel && peer.dataChannel.readyState === 'open') {
      const mensaje = peer.mensajesPendientes.shift();
      if (mensaje) {
        this.enviarAlCanal(peer.dataChannel, mensaje);
      }
    }
  }

  /**
   * Bucle de señalización con polling activo.
   * En cada tick obtiene TODOS los mensajes disponibles en el backend para
   * este peer en una sola petición HTTP y los procesa en orden.
   * Esto es crítico durante el handshake WebRTC donde llegan múltiples
   * ICE candidates en ráfaga y no podemos perder ninguno por latencia.
   */
  private async bucleSeñalizacion(): Promise<void> {
    while (this.bucleSeñalizacionActiva && this.conectada) {
      try {
        const mensajes = await this.recibirSenalizacion();
        for (const mensaje of mensajes) {
          if (!this.conectada) break;
          await this.procesarSenalizacion(mensaje);
        }
      } catch (err) {
        if (!this.conectada) {
          break;
        }
        console.warn('[P2PConexion] Error en bucleSeñalizacion:', err);
      }

      // Intervalo entre ticks de polling
      await this.esperar(INTERVALO_SENALIZACION_MS);
    }
  }

  private async procesarSenalizacion(mensaje: P2PSenalizacionMensaje): Promise<void> {
    // Descartar mensajes de otra sala o mensajes propios (eco)
    if (!mensaje || mensaje.salaId !== this.salaId || mensaje.origenId === this.signalingPeerId) {
      return;
    }

    console.log(`[P2PConexion] Señalización recibida: tipo=${mensaje.tipo} de=${mensaje.origenId}`);

    switch (mensaje.tipo) {
      case 'offer':
        if (this.role !== 'host') {
          return;
        }
        await this.manejarOferta(mensaje);
        return;
      case 'answer':
        if (this.role !== 'player') {
          return;
        }
        await this.manejarRespuesta(mensaje);
        return;
      case 'candidate':
        await this.manejarCandidato(mensaje);
        return;
      default:
        return;
    }
  }

  private async manejarOferta(mensaje: P2PSenalizacionMensaje): Promise<void> {
    console.log(`[P2PConexion] Host procesando offer de ${mensaje.origenId}`);
    const peer = this.obtenerOCrearPeer(mensaje.origenId);
    const descripcion = JSON.parse(mensaje.payload) as RTCSessionDescriptionInit;

    await peer.peerConnection.setRemoteDescription(descripcion);
    await this.aplicarCandidatosPendientes(peer);

    const answer = await peer.peerConnection.createAnswer();
    await peer.peerConnection.setLocalDescription(answer);

    console.log(`[P2PConexion] Host enviando answer → ${mensaje.origenId}`);
    await this.enviarSenalizacion({
      tipo: 'answer',
      salaId: this.salaId,
      origenId: this.peerId,
      destinoId: mensaje.origenId,
      payload: JSON.stringify(peer.peerConnection.localDescription),
      timestamp: Date.now(),
    });
  }

  private async manejarRespuesta(mensaje: P2PSenalizacionMensaje): Promise<void> {
    console.log(`[P2PConexion] Player procesando answer de ${mensaje.origenId}`);
    // Buscar el peer del host (creado en enviarOfferAlHost)
    const peer = this.peers.get(this.hostPeerId);
    if (!peer) {
      console.warn(`[P2PConexion] Player recibió answer pero no hay peer para hostPeerId=${this.hostPeerId}`);
      return;
    }

    const descripcion = JSON.parse(mensaje.payload) as RTCSessionDescriptionInit;
    await peer.peerConnection.setRemoteDescription(descripcion);
    await this.aplicarCandidatosPendientes(peer);
  }

  private async manejarCandidato(mensaje: P2PSenalizacionMensaje): Promise<void> {
    // Para el host: el candidato viene del player (origenId = playerPeerId)
    // Para el player: el candidato viene del host (origenId = hostPeerId)
    const peer = this.obtenerOCrearPeer(mensaje.origenId);
    const candidato = JSON.parse(mensaje.payload) as RTCIceCandidateInit;

    if (peer.peerConnection.remoteDescription) {
      try {
        await peer.peerConnection.addIceCandidate(candidato);
      } catch (err) {
        console.warn('[P2PConexion] Error al añadir ICE candidate:', err);
      }
      return;
    }

    // Si aún no tenemos remoteDescription, guardar para después
    peer.candidatosPendientes.push(candidato);
  }

  private async aplicarCandidatosPendientes(peer: PeerState): Promise<void> {
    while (peer.candidatosPendientes.length > 0) {
      const candidato = peer.candidatosPendientes.shift();
      if (candidato) {
        try {
          await peer.peerConnection.addIceCandidate(candidato);
        } catch (err) {
          console.warn('[P2PConexion] Error al aplicar candidato pendiente:', err);
        }
      }
    }
  }

  private cerrarPeer(peer: PeerState): void {
    try {
      peer.dataChannel?.close();
    } catch { /* ignorar */ }

    try {
      peer.peerConnection.close();
    } catch { /* ignorar */ }
  }

  private async enviarSenalizacion(mensaje: P2PSenalizacionMensaje): Promise<void> {
    const response = await fetch(
      `${this.signalingBaseUrl}/api/p2p/salas/${encodeURIComponent(this.salaId)}/mensajes`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(mensaje),
      }
    );

    if (!response.ok) {
      throw new Error(`No se pudo enviar la señalizacion (${response.status})`);
    }
  }

  /**
   * Obtiene TODOS los mensajes pendientes del backend para este peer.
   * El backend devuelve una lista JSON (puede ser vacía []).
   */
  private async recibirSenalizacion(): Promise<P2PSenalizacionMensaje[]> {
    const response = await fetch(
      `${this.signalingBaseUrl}/api/p2p/salas/${encodeURIComponent(this.salaId)}/mensajes?peerId=${encodeURIComponent(this.signalingPeerId)}`,
      {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
        credentials: 'include',
      }
    );

    if (!response.ok) {
      throw new Error(`No se pudo recibir la señalizacion (${response.status})`);
    }

    return (await response.json()) as P2PSenalizacionMensaje[];
  }

  private limitadorBroadcastPendientes(): void {
    const maximo = 20;
    if (this.mensajesBroadcastPendientes.length > maximo) {
      this.mensajesBroadcastPendientes = this.mensajesBroadcastPendientes.slice(-maximo);
    }
  }

  private limitadorColaPeer(peer: PeerState): void {
    const maximo = 20;
    if (peer.mensajesPendientes.length > maximo) {
      peer.mensajesPendientes = peer.mensajesPendientes.slice(-maximo);
    }
  }

  private async esperar(ms: number): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, ms));
  }
}
