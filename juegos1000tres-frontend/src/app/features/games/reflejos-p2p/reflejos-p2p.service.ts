import { Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { Envio, Recibo, Traductor } from '../../../core/comunicacion';
import { P2PConexion } from '../../../core/comunicacion/implementaciones/p2p-conexion';
import { obtenerApiBaseUrl } from '../../../core/config/api-base';
import { ReflejosReaccionEvento } from './comunicacion/reflejos-reaccion-evento';
import { ReflejosResultadoEvento } from './comunicacion/reflejos-resultado-evento';
import { ReflejosSolicitudSincronizacionEvento } from './comunicacion/reflejos-solicitud-evento';
import { ReflejosSecuenciaEvento } from './comunicacion/reflejos-secuencia-evento';
import { ReflejosSolicitudSincronizacionEnviable } from './comunicacion/reflejos-solicitud-enviable';
import { ReflejosSecuenciaEnviable } from './comunicacion/reflejos-secuencia-enviable';
import { ReflejosReaccionEnviable } from './comunicacion/reflejos-reaccion-enviable';
import { ReflejosResultadoEnviable } from './comunicacion/reflejos-resultado-enviable';
import { ReflejosEstado, ReflejosPaso, ReflejosResultadoItem, ReflejosResultadoPayload, ReflejosSecuenciaPayload } from './comunicacion/reflejos.model';

/** Tiempo máximo (ms) que el player espera conectividad P2P antes de considerarlo fallido */
const CONECTIVIDAD_TIMEOUT_MS = 20_000;

/** Tiempo máximo (ms) que el player espera recibir la secuencia tras conectarse */
const ESPERA_SECUENCIA_TIMEOUT_MS = 5_000;

/** Máximo de reintentos de solicitud de sincronización */
const MAX_REINTENTOS_SYNC = 4;

/** Duración máxima de la fase fuego y límite de tiempo antes de dar la ronda por terminada */
export const DURACION_MAXIMA_DESPUES_DE_FUEGO_MS = 3000;

/** Multiplicador para penalizar pulsaciones antes del fuego (1.0 = 3000ms) */
export const MULTIPLICADOR_PENALIZACION = 1.0;

@Injectable({ providedIn: 'root' })
export class ReflejosP2PService {
  private readonly estado$ = new BehaviorSubject<ReflejosEstado>(this.estadoInicial());
  private traductor: Traductor<string> | null = null;
  private conexion: P2PConexion | null = null;
  private recepcionActiva = false;
  private salaId = '';
  private peerId = '';
  private jugadorSalaId = '';
  private hostId = '';
  private rol: 'host' | 'player' | 'pantalla' = 'player';
  private secuenciaActual: ReflejosSecuenciaPayload | null = null;
  private inicioRondaLocalMs = 0;
  private timer: ReturnType<typeof setInterval> | null = null;
  private reaccionesLocales = new Map<string, number>();
  private victoriaRegistradaEnBackend = false;
  private resultadoBackendPendiente: Promise<void> | null = null;

  constructor(private readonly ngZone: NgZone, private readonly http: HttpClient) {}

  getEstado$(): Observable<ReflejosEstado> {
    return this.estado$.asObservable();
  }

  getPeerId(): string {
    return this.peerId;
  }

  esHost(): boolean {
    return this.rol === 'host';
  }

  esPantalla(): boolean {
    return this.rol === 'pantalla';
  }

  inicializar(salaId: string, rol: 'host' | 'player' | 'pantalla', hostId?: string, jugadorId?: string): void {
    if (!salaId || salaId.trim() === '') {
      throw new Error('La sala es obligatoria');
    }

    this.desconectar();
    this.salaId = salaId.trim();
    this.rol = rol;
    this.jugadorSalaId = jugadorId?.trim() || '';

    // El hostId DEBE venir en los parámetros (generado por el backend)
    const hostIdFinal = hostId?.trim() || '';
    if (!hostIdFinal) {
      throw new Error('El hostPeerId es obligatorio. El backend debe haberlo generado.');
    }

    // IMPORTANTE: El HOST debe usar el peerId que el backend generó (en hostId)
    // El PLAYER genera su propio peerId local o usa su jugadorId
    if (rol === 'host') {
      this.peerId = hostIdFinal;  // Usar el peerId del backend
    } else {
      this.peerId = jugadorId || this.generarPeerId();  // Generar uno propio para el player si no tiene
    }

    this.hostId = hostIdFinal;

    this.conexion = new P2PConexion(this.salaId, `reflejos:${this.salaId}`, {
      peerId: this.peerId,
      canalNombre: `reflejos:${this.salaId}`,
      role: rol === 'host' ? 'host' : 'player',
      hostPeerId: this.hostId,
      signalingBaseUrl: obtenerApiBaseUrl(),
      onPeerConnected: (peerId) => this.reenviarEstadoActualSiEsHost(peerId),
    });

    // Construir Recibo con todos los eventos del dominio (Arquitectura Traductor)
    const recibo = new Recibo(String, Recibo.extractorComandoDesdeJson())
      .conEvento('SECUENCIA_REFLEJOS', new ReflejosSecuenciaEvento((payload) => this.recibirSecuencia(payload)))
      .conEvento('REGISTRAR_REACCION', new ReflejosReaccionEvento((payload) => this.recibirReaccion(payload)))
      .conEvento('RESULTADO_REFLEJOS', new ReflejosResultadoEvento((payload) => this.recibirResultado(payload)))
      .conEvento('SOLICITAR_SINCRONIZACION', new ReflejosSolicitudSincronizacionEvento((payload) => this.recibirSolicitudSincronizacion(payload)));

    this.traductor = new Traductor(this.conexion, Envio.paraStringDesdeOut(), recibo);
    this.traductor.conectar();

    // Mostrar estado inicial apropiado para cada rol
    this.estado$.next({
      rondaId: '',
      fase: 'ESPERANDO',
      palabraActual: '',
      mensaje: rol === 'host' ? 'Esperando jugadores...' : 'Conectando con el host...',
      inicioLocalMs: 0,
      fuegoLocalMs: 0,
      duracionMs: 0,
      ranking: [],
      ganadorId: null,
      reacciones: [],
    });

    this.recepcionActiva = true;
    void this.bucleRecepcion();

    if (this.esHost()) {
      void this.iniciarComoHost();
    } else {
      void this.iniciarComoPlayer();
    }
  }

  private async iniciarComoHost(): Promise<void> {
    console.log('[ReflejosP2P] iniciarComoHost() - Esperando conectividad P2P...');
    try {
      if (this.conexion) {
        await this.conexion.esperarConectividadP2P(CONECTIVIDAD_TIMEOUT_MS);
        console.log('[ReflejosP2P] iniciarComoHost() - Conectividad P2P establecida con players.');
      }
    } catch (error) {
      console.warn('[ReflejosP2P] Timeout esperando conexión P2P de players, iniciando ronda de todas formas', error);
    }

    console.log('[ReflejosP2P] programarRondaInicial() se va a ejecutar ahora.');
    this.programarRondaInicial();
  }

  private async iniciarComoPlayer(): Promise<void> {
    console.log('[ReflejosP2P] iniciarComoPlayer() - Esperando conectividad P2P...');
    try {
      if (this.conexion) {
        this.actualizarEstado({ mensaje: 'Estableciendo conexión P2P...' });
        await this.conexion.esperarConectividadP2P(CONECTIVIDAD_TIMEOUT_MS);
        console.log('[ReflejosP2P] iniciarComoPlayer() - Conectividad P2P establecida con host.');
      }
    } catch (err) {
      console.warn('[ReflejosP2P] Timeout esperando conectividad P2P como player:', err);
      this.actualizarEstado({ mensaje: 'No se pudo conectar al host. Reintentando...' });
      return;
    }

    if (!this.recepcionActiva) {
      console.warn('[ReflejosP2P] iniciarComoPlayer() - recepcionActiva es false, abortando.');
      return;
    }

    console.log('[ReflejosP2P] iniciarComoPlayer() - Solicitando sincronización con el host...');
    this.actualizarEstado({ mensaje: 'Conectado. Solicitando estado del juego...' });

    await this.solicitarSincronizacionConReintentos();
  }

  /**
   * Solicita sincronización al host y, si no llega la secuencia en el tiempo esperado,
   * reintenta hasta MAX_REINTENTOS_SYNC veces.
   */
  private async solicitarSincronizacionConReintentos(): Promise<void> {
    for (let intento = 0; intento < MAX_REINTENTOS_SYNC; intento++) {
      if (!this.recepcionActiva || !this.conexion) {
        break;
      }

      this.solicitarSincronizacion();

      // Esperar a ver si llega la secuencia
      await this.esperar(ESPERA_SECUENCIA_TIMEOUT_MS);

      if (this.secuenciaActual !== null) {
        // La secuencia llegó correctamente a través del bucleRecepcion
        return;
      }

      if (intento < MAX_REINTENTOS_SYNC - 1) {
        console.warn(`[ReflejosP2P] No llegó secuencia en ${ESPERA_SECUENCIA_TIMEOUT_MS}ms. Reintentando (${intento + 1}/${MAX_REINTENTOS_SYNC})...`);
        this.actualizarEstado({ mensaje: `Esperando secuencia del host... (intento ${intento + 2})` });
      }
    }

    if (this.secuenciaActual === null && this.recepcionActiva) {
      // El host aún no ha iniciado el juego, actualizar mensaje de espera
      this.actualizarEstado({ mensaje: 'Esperando que el host inicie la ronda...' });
    }
  }

  registrarReaccion(): void {
    if (this.esPantalla() || !this.traductor || !this.secuenciaActual) {
      return;
    }

    const ahora = Date.now();
    const payload = new ReflejosReaccionEnviable({
      comando: 'REGISTRAR_REACCION',
      rondaId: this.secuenciaActual.rondaId,
      jugadorId: this.peerId,
      tiempoMs: ahora,
    });

    this.traductor.enviar(payload);
    this.reaccionesLocales.set(this.peerId, ahora);
    this.actualizarEstadoDesdeReacciones();
  }

  desconectar(): void {
    this.recepcionActiva = false;

    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }

    this.traductor?.desconectar();
    this.traductor = null;
    this.conexion?.desconectar();
    this.conexion = null;
    this.estado$.next(this.estadoInicial());
    this.secuenciaActual = null;
    this.reaccionesLocales.clear();
    this.inicioRondaLocalMs = 0;
    this.victoriaRegistradaEnBackend = false;
    this.resultadoBackendPendiente = null;
  }

  finalizarJuegoEnBackend(actorId: string): Promise<void> {
    if (!this.esHost() || !this.salaId || !actorId) return Promise.resolve();

    const resultadoPendiente = this.resultadoBackendPendiente ?? Promise.resolve();

    return resultadoPendiente.finally(() => new Promise<void>((resolve) => {
      this.cerrarJuego(actorId, () => resolve());
    }));
  }

  private cerrarJuego(actorId: string, resolve: () => void): void {
    const url = `${obtenerApiBaseUrl()}/sala/${this.salaId}/juego/finalizar?actorId=${actorId}`;
    this.http.post(url, null, { withCredentials: true }).subscribe({
      next: () => resolve(),
      error: (e) => {
        console.error('[ReflejosP2P] Error al finalizar el juego en el backend:', e);
        resolve(); // Resolvemos igual para no bloquear
      }
    });
  }

  private registrarVictoriaGanadorEnBackend(ganadorId: string | null): Promise<void> {
    if (!this.esHost() || !this.salaId || this.victoriaRegistradaEnBackend) {
      return Promise.resolve();
    }

    const ganadorSalaId = this.normalizarGanadorParaSala(ganadorId);
    if (!ganadorSalaId || ganadorSalaId === 'Empate' || ganadorSalaId === 'Nadie') {
      return Promise.resolve();
    }

    this.victoriaRegistradaEnBackend = true;

    const urlVictoria = `${obtenerApiBaseUrl()}/sala/${this.salaId}/victoria?jugadorId=${encodeURIComponent(ganadorSalaId)}`;
    const urlPuntuacion = `${obtenerApiBaseUrl()}/sala/${this.salaId}/puntuacion?jugadorId=${encodeURIComponent(ganadorSalaId)}&puntos=1`;

    this.resultadoBackendPendiente = new Promise<void>((resolve) => {
      this.http.post(urlVictoria, null, { withCredentials: true }).subscribe({
        next: () => {
          this.http.post(urlPuntuacion, null, { withCredentials: true }).subscribe({
            next: () => resolve(),
            error: (e) => {
              console.error('[ReflejosP2P] Error al registrar puntuacion del ganador:', e);
              this.victoriaRegistradaEnBackend = false;
              resolve();
            }
          });
        },
        error: (e) => {
          console.error('[ReflejosP2P] Error al registrar victoria:', e);
          this.victoriaRegistradaEnBackend = false;
          resolve();
        }
      });
    });

    return this.resultadoBackendPendiente;
  }

  private normalizarGanadorParaSala(ganadorId: string | null): string {
    if (!ganadorId) {
      return '';
    }

    if (this.jugadorSalaId && ganadorId === this.hostId) {
      return this.jugadorSalaId;
    }

    return ganadorId;
  }

  /**
   * Bucle principal de recepción de mensajes P2P.
   * Usa Traductor.recibirPayload() que internamente llama a P2PConexion.recibir(),
   * el cual tiene un timeout para no bloquearse indefinidamente.
   */
  private async bucleRecepcion(): Promise<void> {
    while (this.recepcionActiva && this.traductor) {
      try {
        const payload = await this.traductor.recibirPayload();

        if (typeof payload !== 'string' || !payload.trim()) {
          continue;
        }

        this.traductor.procesar(payload);
      } catch (err) {
        if (!this.recepcionActiva) {
          break;
        }

        // Si es un timeout de recibir(), no es un error grave, simplemente no hay mensajes
        const mensaje = err instanceof Error ? err.message : '';
        if (!mensaje.includes('timeout')) {
          console.warn('[ReflejosP2P] Error en bucleRecepcion:', err);
        }

        // Pequeña pausa antes de reintentar para no saturar en caso de error real
        await this.esperar(100);
      }
    }
  }

  private programarRondaInicial(): void {
    console.log('[ReflejosP2P] Generando y enviando secuencia de ronda inicial...');
    const rondaId = `r-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
    const pasos = this.generarSecuencia();
    const duracionMs = pasos.reduce((total, paso) => total + paso.retrasoMs, 0);
    const inicioLocalMs = Date.now() + 2000;

    const payload: ReflejosSecuenciaPayload = {
      comando: 'SECUENCIA_REFLEJOS',
      rondaId,
      hostId: this.peerId,
      inicioLocalMs,
      duracionMs,
      pasos,
    };

    this.secuenciaActual = payload;
    this.inicioRondaLocalMs = inicioLocalMs;
    this.traductor?.enviar(new ReflejosSecuenciaEnviable(payload));

    console.log('[ReflejosP2P] Secuencia enviada. Iniciando cronómetro local...');
    this.iniciarCronometroLocal();
  }

  private recibirSecuencia(payload: ReflejosSecuenciaPayload): void {
    console.log('[ReflejosP2P] recibirSecuencia() - Payload:', payload);
    if (!payload || payload.hostId === this.peerId) {
      console.log('[ReflejosP2P] recibirSecuencia() - Ignorado (propio hostId o payload inválido)');
      return;
    }

    this.secuenciaActual = payload;
    this.hostId = payload.hostId;
    this.inicioRondaLocalMs = payload.inicioLocalMs;
    this.reaccionesLocales.clear();
    this.actualizarEstado({
      rondaId: payload.rondaId,
      fase: 'CUENTA_ATRAS',
      palabraActual: 'Preparando...',
      mensaje: 'Esperando la secuencia',
      inicioLocalMs: payload.inicioLocalMs,
      fuegoLocalMs: 0,
      duracionMs: payload.duracionMs,
      ranking: [],
      ganadorId: null,
      reacciones: [],
    });

    this.iniciarCronometroLocal();
  }

  private recibirReaccion(payload: { rondaId: string; jugadorId: string; tiempoMs: number }): void {
    if (!this.secuenciaActual || payload.rondaId !== this.secuenciaActual.rondaId) {
      return;
    }

    this.reaccionesLocales.set(payload.jugadorId, payload.tiempoMs);
    this.actualizarEstadoDesdeReacciones();
  }

  private recibirResultado(payload: ReflejosResultadoPayload): void {
    if (!payload || (this.secuenciaActual && payload.rondaId !== this.secuenciaActual.rondaId)) {
      return;
    }

    this.actualizarEstado({
      ...this.estado$.value,
      fase: 'RESULTADO',
      ganadorId: payload.ganadorId,
      ranking: payload.ranking,
      mensaje: payload.ganadorId ? `Ganador: ${payload.ganadorId}` : 'Sin ganador',
    });
  }

  private iniciarCronometroLocal(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
    }

    this.timer = setInterval(() => {
      if (!this.secuenciaActual) {
        return;
      }

      const ahora = Date.now();
      const inicio = this.secuenciaActual.inicioLocalMs;
      const pasos = this.secuenciaActual.pasos;
      const fin = inicio + this.secuenciaActual.duracionMs;

      if (ahora < inicio) {
        const restante = Math.max(0, inicio - ahora);
        this.actualizarEstado({
          ...this.estado$.value,
          fase: 'CUENTA_ATRAS',
          palabraActual: 'Preparando duelo',
          mensaje: `Empieza en ${Math.ceil(restante / 1000)}s`,
          inicioLocalMs: inicio,
          fuegoLocalMs: 0,
        });
        return;
      }

      let acumulado = inicio;
      let pasoActual = pasos[0];
      for (let i = 0; i < pasos.length; i++) {
        acumulado += pasos[i].retrasoMs;
        if (ahora >= acumulado && i + 1 < pasos.length) {
          pasoActual = pasos[i + 1];
        }
      }

      const fase: ReflejosEstado['fase'] = pasoActual.tipo === 'FUEGO' ? 'FUEGO' : 'PALABRA';
      const fuegoLocalMs = this.calcularFuegoMs(inicio, pasos);
      this.actualizarEstado({
        ...this.estado$.value,
        fase,
        palabraActual: pasoActual.texto,
        mensaje: pasoActual.tipo === 'FUEGO' ? 'FUEGO' : 'Mantente atento',
        inicioLocalMs: inicio,
        fuegoLocalMs,
        duracionMs: this.secuenciaActual.duracionMs,
        ranking: this.calcularRanking(),
        ganadorId: this.calcularGanadorId(),
        reacciones: this.calcularRanking(),
      });

      if (ahora >= fin) {
        this.publicarResultado();
        if (this.timer !== null) {
          clearInterval(this.timer);
          this.timer = null;
        }
      }
    }, 50);
  }

  private publicarResultado(): void {
    if (!this.traductor || !this.secuenciaActual || !this.esHost()) {
      return;
    }

    const ranking = this.calcularRanking();
    const ganadorId = this.calcularGanadorId();
    const payload: ReflejosResultadoPayload = {
      comando: 'RESULTADO_REFLEJOS',
      rondaId: this.secuenciaActual.rondaId,
      ganadorId,
      ranking,
    };

    this.traductor.enviar(new ReflejosResultadoEnviable(payload));
    this.actualizarEstado({
      ...this.estado$.value,
      fase: 'RESULTADO',
      ranking,
      ganadorId,
      mensaje: ganadorId ? `Ganador: ${ganadorId}` : 'Sin ganador',
    });

    void this.registrarVictoriaGanadorEnBackend(ganadorId);
  }

  private reenviarEstadoActualSiEsHost(_peerId: string): void {
    if (!this.esHost() || !this.secuenciaActual || !this.traductor) {
      return;
    }

    this.traductor.enviar(new ReflejosSecuenciaEnviable(this.secuenciaActual));

    if (this.estado$.value.fase === 'RESULTADO') {
      this.traductor.enviar(new ReflejosResultadoEnviable({
        comando: 'RESULTADO_REFLEJOS',
        rondaId: this.secuenciaActual.rondaId,
        ganadorId: this.calcularGanadorId(),
        ranking: this.calcularRanking(),
      }));
    }
  }

  private solicitarSincronizacion(): void {
    if (!this.traductor) {
      console.warn('[ReflejosP2P] solicitarSincronizacion() abortada: traductor no está inicializado.');
      return;
    }

    console.log('[ReflejosP2P] Enviando SOLICITAR_SINCRONIZACION...');
    this.traductor.enviar(new ReflejosSolicitudSincronizacionEnviable({
      comando: 'SOLICITAR_SINCRONIZACION',
      jugadorId: this.peerId,
      destinoId: this.hostId || undefined,
      rondaId: this.secuenciaActual?.rondaId,
    }));
  }

  private recibirSolicitudSincronizacion(payload: { jugadorId: string; rondaId?: string }): void {
    console.log('[ReflejosP2P] recibirSolicitudSincronizacion() de:', payload?.jugadorId);
    if (!this.esHost() || !this.secuenciaActual || !this.traductor) {
      console.warn('[ReflejosP2P] Ignorando SOLICITAR_SINCRONIZACION. esHost:', this.esHost(), 'secuenciaActual:', !!this.secuenciaActual);
      return;
    }

    console.log('[ReflejosP2P] Reenviando SECUENCIA_REFLEJOS por solicitud de', payload?.jugadorId);
    this.traductor.enviar(new ReflejosSecuenciaEnviable(this.secuenciaActual));
  }

  private generarSecuencia(): ReflejosPaso[] {
    const palabrasNoFuego = ['FUENTE', 'FUTBOL', 'JUEGO', 'PUNTO', 'RAPIDO', 'TIEMPO'];
    const totalPasos = 8;
    const pasosGenerados: ReflejosPaso[] = [];

    for (let i = 0; i < totalPasos; i++) {
      const esFuego = Math.random() < 0.2;
      pasosGenerados.push({
        tipo: esFuego ? 'FUEGO' : (i < 3 ? 'CUENTA_ATRAS' : 'PALABRA'),
        texto: esFuego ? 'FUEGO' : palabrasNoFuego[Math.floor(Math.random() * palabrasNoFuego.length)],
        retrasoMs: 700 + Math.floor(Math.random() * 500),
      });
    }

    let posicionFuego = pasosGenerados.findIndex(p => p.tipo === 'FUEGO');
    if (posicionFuego === -1) {
      posicionFuego = Math.floor(Math.random() * totalPasos);
      pasosGenerados[posicionFuego] = {
        tipo: 'FUEGO',
        texto: 'FUEGO',
        retrasoMs: pasosGenerados[posicionFuego].retrasoMs,
      };
    }

    // La ronda termina exactamente DURACION_MAXIMA_DESPUES_DE_FUEGO_MS después de que FUEGO aparece.
    // Recortamos los pasos que vienen después de FUEGO.
    const pasosFinales = pasosGenerados.slice(0, posicionFuego + 1);
    
    // El retrasoMs del paso FUEGO es lo que tarda en acabar la partida después de aparecer
    pasosFinales[posicionFuego].retrasoMs = DURACION_MAXIMA_DESPUES_DE_FUEGO_MS;

    return pasosFinales;
  }

  private calcularFuegoMs(inicio: number, pasos: ReflejosPaso[]): number {
    let acumulado = inicio;
    for (const paso of pasos) {
      if (paso.tipo === 'FUEGO') {
        return acumulado; // El fuego aparece al INICIO de su paso
      }
      acumulado += paso.retrasoMs;
    }
    return inicio;
  }

  private calcularRanking(): ReflejosResultadoItem[] {
    const fuego = this.calcularFuegoMs(this.inicioRondaLocalMs, this.secuenciaActual?.pasos ?? []);
    const penalizacionMs = DURACION_MAXIMA_DESPUES_DE_FUEGO_MS * MULTIPLICADOR_PENALIZACION;

    return [...this.reaccionesLocales.entries()]
      .map(([jugadorId, tiempoMs]) => {
        let tiempoCalculado = 0;
        if (tiempoMs < fuego) {
          // Penalización por pulsar antes de fuego
          tiempoCalculado = penalizacionMs;
        } else {
          tiempoCalculado = tiempoMs - fuego;
          if (tiempoCalculado > DURACION_MAXIMA_DESPUES_DE_FUEGO_MS) {
            tiempoCalculado = DURACION_MAXIMA_DESPUES_DE_FUEGO_MS;
          }
        }
        return {
          jugadorId,
          tiempoMs: tiempoCalculado,
        };
      })
      .sort((a, b) => a.tiempoMs - b.tiempoMs);
  }

  private calcularGanadorId(): string | null {
    const ranking = this.calcularRanking();
    return ranking.length > 0 ? ranking[0].jugadorId : null;
  }

  private async esperar(ms: number): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, ms));
  }

  private actualizarEstado(parcial: Partial<ReflejosEstado>): void {
    this.ngZone.run(() => {
      this.estado$.next({
        ...this.estado$.value,
        ...parcial,
      });
    });
  }

  private actualizarEstadoDesdeReacciones(): void {
    const ranking = this.calcularRanking();
    this.actualizarEstado({
      ...this.estado$.value,
      ranking,
      reacciones: ranking,
      ganadorId: ranking.length > 0 ? ranking[0].jugadorId : null,
    });
  }

  private estadoInicial(): ReflejosEstado {
    return {
      rondaId: '',
      fase: 'ESPERANDO',
      palabraActual: '',
      mensaje: 'Preparando reflejos',
      inicioLocalMs: 0,
      fuegoLocalMs: 0,
      duracionMs: 0,
      ranking: [],
      ganadorId: null,
      reacciones: [],
    };
  }

  private generarPeerId(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }

    return `peer-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
  }
}
