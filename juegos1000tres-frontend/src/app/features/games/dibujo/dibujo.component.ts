import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, OnInit, AfterViewInit, ViewChild, ElementRef, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Enviable, Envio, Recibo, Traductor, WebSocketConexion } from '../../../core/comunicacion';

type EstadoDibujo = {
  comando: string;
  fase: string;
  enCurso: boolean;
  tema: string;
  mensaje: string;
  jugadorTemaId?: string;
  jugadorDibujaId?: string;
  palabraSecreta?: string;
  ganadores?: string[];
  ganadoresNombres?: string[];
  marcador?: Array<{ jugadorId: string; nombre: string; puntos: number }>;
  drawingFrame?: Array<Record<string, unknown>>;
  resumenRonda?: Array<Record<string, unknown>>;
  puedeProponerTema?: boolean;
  puedeAdivinar?: boolean;
  resultadoIntento?: string;
  tiempoRestanteMs?: number;
  rondaActual?: number;
  totalRondas?: number;
};

@Component({
  selector: 'app-dibujo',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dibujo.component.html',
  styleUrls: ['./dibujo.component.css'],
})
export class DibujoComponent implements OnInit, AfterViewInit, OnDestroy {
  @Input() uuid = '';
  @Input() jugadorId = '';
  @Input() pantallaId = '';
  @Input() esPantalla = false;
  @Input() esHost = false;
  @Output() volverSala = new EventEmitter<void>();

  estadoConexion = 'Preparando WebSocket...';
  nombreJugador = 'Jugador';
  tema = '';
  intento = '';
  estado: Partial<EstadoDibujo> = {};

  private traductor?: Traductor<string>;
  private recepcionActiva = false;
  private registroEnviado = false;
  private jugadorPersistente = '';

  // Dibujo interactivo del pintor
  private pintando = false;
  private ultimoX = 0;
  private ultimoY = 0;
  private tiempoInicioRonda = 0;
  private finRondaEpochMs = 0;
  private relojId: ReturnType<typeof setInterval> | null = null;
  private ahoraMs = Date.now();
  private rondaVisible = 0;

  @ViewChild('canvas') canvasRef?: ElementRef<HTMLCanvasElement>;

  ngOnInit(): void {
    this.nombreJugador = this.nombreInicial();
    this.jugadorPersistente = this.obtenerJugadorPersistente();
    this.relojId = setInterval(() => {
      this.ahoraMs = Date.now();
    }, 250);
    this.inicializarComunicacion();
    this.iniciarRecepcion();
  }

  ngAfterViewInit(): void {
    // Redimensionar e inicializar canvas al cargar si existe
    if (this.canvasRef) {
      this.ajustarCanvas();
    }
  }

  ngOnDestroy(): void {
    this.recepcionActiva = false;
    if (this.relojId) {
      clearInterval(this.relojId);
      this.relojId = null;
    }
    this.traductor?.desconectar();
    this.traductor = undefined;
  }

  proponerTema(): void {
    const temaLimpio = this.tema.trim();
    if (!temaLimpio || !this.traductor) return;

    this.traductor.enviar(new ProponerTemaEnviable(this.idJugadorActual, this.nombreJugador, temaLimpio));
    this.tema = '';
  }

  intentarAdivinar(): void {
    const intentoLimpio = this.intento.trim();
    if (!intentoLimpio || !this.traductor) return;

    this.traductor.enviar(new IntentarAdivinarEnviable(this.idJugadorActual, this.nombreJugador, intentoLimpio));
    this.intento = '';
  }

  get esProponenteTema(): boolean {
    return !!this.estado.jugadorTemaId && this.idJugadorActual === this.estado.jugadorTemaId;
  }

  get esDibujante(): boolean {
    return this.esProponenteTema;
  }

  get esPintor(): boolean {
    return !!this.estado.jugadorDibujaId && this.idJugadorActual === this.estado.jugadorDibujaId;
  }

  get yaAcerto(): boolean {
    if (!this.estado.resumenRonda) return false;
    return this.estado.resumenRonda.some(
      (item) => item['adivinador'] === this.idJugadorActual && item['acierta'] === true
    );
  }

  get segundosRestantes(): number {
    if (this.estado.fase === 'JUGANDO' && this.finRondaEpochMs > 0) {
      return Math.max(0, Math.ceil((this.finRondaEpochMs - this.ahoraMs) / 1000));
    }
    return Math.max(0, Math.ceil((this.estado.tiempoRestanteMs ?? 0) / 1000));
  }

  get esFaseFinalizada(): boolean {
    return this.estado.fase === 'FINALIZADA';
  }

  get ganadoresNombresSeguros(): string[] {
    return this.estado.ganadoresNombres ?? [];
  }

  private get idJugadorActual(): string {
    return this.jugadorId?.trim() || this.jugadorPersistente;
  }

  esGanador(jugador: { jugadorId: string; nombre: string }): boolean {
    const ganadores = this.estado.ganadores ?? [];
    return ganadores.includes(jugador.jugadorId) || ganadores.includes(jugador.nombre);
  }

  volverALaSala(): void {
    if (!this.esHost || !this.esFaseFinalizada) {
      return;
    }

    this.volverSala.emit();
  }

  // Métodos del lienzo para el pintor
  iniciarDibujo(e: MouseEvent | TouchEvent): void {
    if (!this.esPintor || this.estado.fase !== 'JUGANDO') return;
    this.pintando = true;
    const coords = this.obtenerCoordenadas(e);
    if (!coords) return;
    this.ultimoX = coords.x;
    this.ultimoY = coords.y;

    this.enviarPunto(coords.x, coords.y, true);
  }

  dibujar(e: MouseEvent | TouchEvent): void {
    if (!this.pintando || !this.esPintor || this.estado.fase !== 'JUGANDO') return;
    e.preventDefault();
    const coords = this.obtenerCoordenadas(e);
    if (!coords) return;

    this.dibujarLineaLocal(this.ultimoX, this.ultimoY, coords.x, coords.y);

    this.ultimoX = coords.x;
    this.ultimoY = coords.y;

    this.enviarPunto(coords.x, coords.y, false);
  }

  detenerDibujo(): void {
    this.pintando = false;
  }

  deshacer(): void {
    if (!this.esPintor || !this.traductor) return;
    this.traductor.enviar(new ComandoVacioEnviable(this.idJugadorActual, 'ERASE_LAST'));
  }

  limpiar(): void {
    if (!this.esPintor || !this.traductor) return;
    this.traductor.enviar(new ComandoVacioEnviable(this.idJugadorActual, 'CLEAR_DRAWING'));
  }

  private obtenerCoordenadas(e: MouseEvent | TouchEvent): { x: number; y: number } | null {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return null;

    const rect = canvas.getBoundingClientRect();
    let clientX = 0;
    let clientY = 0;

    if (e instanceof MouseEvent) {
      clientX = e.clientX;
      clientY = e.clientY;
    } else if (e.touches && e.touches.length > 0) {
      clientX = e.touches[0].clientX;
      clientY = e.touches[0].clientY;
    } else {
      return null;
    }

    const relX = (clientX - rect.left) / rect.width;
    const relY = (clientY - rect.top) / rect.height;

    // Escalar al lienzo lógico de 800x600
    const x = Math.round(relX * 800);
    const y = Math.round(relY * 600);

    return { x, y };
  }

  private dibujarLineaLocal(x1: number, y1: number, x2: number, y2: number): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.strokeStyle = '#1e293b';
    ctx.lineWidth = 4;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    ctx.beginPath();
    const scaleX = canvas.width / 800;
    const scaleY = canvas.height / 600;

    ctx.moveTo(x1 * scaleX, y1 * scaleY);
    ctx.lineTo(x2 * scaleX, y2 * scaleY);
    ctx.stroke();
  }

  private enviarPunto(x: number, y: number, down: boolean): void {
    if (!this.traductor) return;
    const t = Date.now() - this.tiempoInicioRonda;
    this.traductor.enviar(new AddStrokeEnviable(this.idJugadorActual, x, y, t, down));
  }

  private ajustarCanvas(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;
    canvas.width = canvas.clientWidth;
    canvas.height = canvas.clientHeight;
  }

  private inicializarComunicacion(): void {
    const salaId = this.salaId();
    const rol = this.esPantalla ? 'pantalla' : 'jugadores';
    const baseCanal = `ws://127.0.0.1:8091/ws/salas/${encodeURIComponent(salaId)}/${rol}`;
    const canal = this.esPantalla
      ? baseCanal
      : `${baseCanal}?jugadorId=${encodeURIComponent(this.idJugadorActual)}`;

    const conexion = new WebSocketConexion(salaId, canal);
    const envio = Envio.paraStringDesdeOut();
    const recibo = new Recibo(String, Recibo.extractorComandoDesdeJson())
      .conEvento('ESTADO_DIBUJO', {
        hacer: (payload: string) => this.procesarEstado(payload),
      });

    this.traductor = new Traductor(conexion, envio, recibo);
    this.traductor.conectar();
    this.estadoConexion = `Conectando a ${rol} de ${salaId}...`;
  }

  private async bucleRecepcion(): Promise<void> {
    while (this.recepcionActiva && this.traductor) {
      const conexion = this.obtenerConexionWebSocket();

      if (conexion && !conexion.estaConectado()) {
        this.estadoConexion = 'Reconectando...';
        this.traductor.conectar();
        await this.esperar(500);
        continue;
      }

      if (!this.esPantalla && !this.registroEnviado && conexion?.estaConectado()) {
        try {
          this.traductor.enviar(new RegistrarJugadorEnviable(this.idJugadorActual, this.nombreJugador));
          this.registroEnviado = true;
        } catch {
          await this.esperar(200);
          continue;
        }
      }

      try {
        const payload = await this.traductor.recibirPayload();
        if (typeof payload !== 'string' || !payload.trim()) {
          continue;
        }

        this.traductor.procesar(payload);
        this.estadoConexion = 'Conectado';
      } catch (error: unknown) {
        if (!this.recepcionActiva) break;

        this.estadoConexion = `Conexion inestable: ${this.formatearError(error)}`;
        await this.esperar(300);
      }
    }
  }

  private procesarEstado(payload: string): void {
    try {
      const data = JSON.parse(payload) as Record<string, unknown>;
      if (data['comando'] !== 'ESTADO_DIBUJO') return;

      const anteriorFase = this.estado.fase;

      this.estado = {
        comando: String(data['comando'] ?? ''),
        fase: String(data['fase'] ?? ''),
        enCurso: Boolean(data['enCurso']),
        tema: String(data['tema'] ?? ''),
        mensaje: String(data['mensaje'] ?? ''),
        jugadorTemaId: typeof data['jugadorTemaId'] === 'string' ? String(data['jugadorTemaId']) : undefined,
        jugadorDibujaId: typeof data['jugadorDibujaId'] === 'string' ? String(data['jugadorDibujaId']) : undefined,
        palabraSecreta: typeof data['palabraSecreta'] === 'string' ? String(data['palabraSecreta']) : undefined,
        resultadoIntento: typeof data['resultadoIntento'] === 'string' ? String(data['resultadoIntento']) : undefined,
        ganadores: Array.isArray(data['ganadores']) ? data['ganadores'].map((item) => String(item)) : [],
        ganadoresNombres: Array.isArray(data['ganadoresNombres']) ? data['ganadoresNombres'].map((item) => String(item)) : [],
        marcador: Array.isArray(data['marcador'])
          ? (data['marcador'] as Array<Record<string, unknown>>).map(m => ({ jugadorId: String(m['jugadorId'] ?? ''), nombre: String(m['nombre'] ?? ''), puntos: Number(m['puntos'] ?? 0) }))
          : [],
        drawingFrame: Array.isArray(data['drawingFrame']) ? (data['drawingFrame'] as Array<Record<string, unknown>>) : [],
        resumenRonda: Array.isArray(data['resumenRonda']) ? (data['resumenRonda'] as Array<Record<string, unknown>>) : [],
        puedeProponerTema: Boolean(data['puedeProponerTema']),
        puedeAdivinar: Boolean(data['puedeAdivinar']),
        tiempoRestanteMs: typeof data['tiempoRestanteMs'] === 'number' ? Number(data['tiempoRestanteMs']) : undefined,
        rondaActual: typeof data['rondaActual'] === 'number' ? Number(data['rondaActual']) : undefined,
        totalRondas: typeof data['totalRondas'] === 'number' ? Number(data['totalRondas']) : undefined,
      };

      if (this.estado.fase === 'JUGANDO' && anteriorFase !== 'JUGANDO') {
        this.tiempoInicioRonda = Date.now();
      }

      if (this.estado.fase === 'JUGANDO') {
        const rondaActual = this.estado.rondaActual ?? 0;
        const restanteMsPayload = this.estado.tiempoRestanteMs ?? 0;
        const restanteMs = restanteMsPayload > 0 ? restanteMsPayload : 60_000;
        if (anteriorFase !== 'JUGANDO' || rondaActual !== this.rondaVisible) {
          this.finRondaEpochMs = Date.now() + Math.max(0, restanteMs);
          this.rondaVisible = rondaActual;
          this.tiempoInicioRonda = Date.now();
        }
      } else {
        this.finRondaEpochMs = 0;
      }

      // Solo la pantalla compartida repinta el frame remoto; el pintor usa su dibujo local.
      if (this.canvasRef && this.esPantalla && this.estado.drawingFrame) {
        this.renderizarFrame(this.estado.drawingFrame);
      }
    } catch {
      // ignorar
    }
  }

  private renderizarFrame(frame: Array<Record<string, unknown>>): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Sincronizar dimensiones físicas
    if (canvas.width !== canvas.clientWidth || canvas.height !== canvas.clientHeight) {
      canvas.width = canvas.clientWidth;
      canvas.height = canvas.clientHeight;
    }

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    ctx.strokeStyle = '#1e293b';
    ctx.lineWidth = 4;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    let lastX: number | undefined;
    let lastY: number | undefined;

    const scaleX = canvas.width / 800;
    const scaleY = canvas.height / 600;

    for (const seg of frame) {
      const lx = typeof seg['x'] === 'number' ? (seg['x'] as number) : undefined;
      const ly = typeof seg['y'] === 'number' ? (seg['y'] as number) : undefined;
      const down = Boolean(seg['down']);
      if (lx === undefined || ly === undefined) continue;

      const px = lx * scaleX;
      const py = ly * scaleY;

      if (down || lastX === undefined || lastY === undefined) {
        ctx.beginPath();
        ctx.moveTo(px, py);
      } else {
        ctx.beginPath();
        ctx.moveTo(lastX, lastY);
        ctx.lineTo(px, py);
        ctx.stroke();
      }
      lastX = px;
      lastY = py;
    }
  }

  private registrarJugadorSiHaceFalta(): void {
    if (this.esPantalla || this.registroEnviado || !this.traductor) return;
    this.traductor.enviar(new RegistrarJugadorEnviable(this.idJugadorActual, this.nombreJugador));
    this.registroEnviado = true;
  }

  private obtenerJugadorPersistente(): string {
    const clave = this.claveStorageJugador();
    const actual = localStorage.getItem(clave);

    if (actual && actual.trim()) return actual.trim();

    const nuevo = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `jug-${Date.now()}-${Math.floor(Math.random() * 99999)}`;

    localStorage.setItem(clave, nuevo);
    return nuevo;
  }

  private nombreInicial(): string {
    const clave = this.claveStorageNombre();
    const actual = localStorage.getItem(clave);
    if (actual && actual.trim()) return actual.trim();
    return 'Jugador';
  }

  private salaId(): string {
    return this.uuid?.trim() || 'dibujo';
  }

  private claveStorageJugador(): string {
    return `dibujo_jugador_id:${this.salaId()}`;
  }

  private claveStorageNombre(): string {
    return `dibujo_nombre:${this.salaId()}`;
  }

  private formatearError(error: unknown): string {
    if (error instanceof Error) return error.message;
    return String(error);
  }

  private obtenerConexionWebSocket(): WebSocketConexion | undefined {
    const conexion = this.traductor?.getConexion();
    return conexion instanceof WebSocketConexion ? conexion : undefined;
  }

  private esperar(ms: number): Promise<void> {
    return new Promise((resolve) => window.setTimeout(resolve, ms));
  }

  private iniciarRecepcion(): void {
    this.recepcionActiva = true;
    void this.bucleRecepcion();
  }
}

class RegistrarJugadorEnviable extends Enviable {
  constructor(private readonly jugadorId: string, private readonly nombreJugador: string) { super(); }
  out(): string {
    return JSON.stringify({ comando: 'REGISTRAR_JUGADOR', jugadorId: this.jugadorId, nombreJugador: this.nombreJugador });
  }
  in(entrada: unknown): void { void entrada; }
}

class ProponerTemaEnviable extends Enviable {
  constructor(private readonly jugadorId: string, private readonly nombreJugador: string, private readonly tema: string) { super(); }
  out(): string { return JSON.stringify({ comando: 'PROPONER_TEMA', jugadorId: this.jugadorId, nombreJugador: this.nombreJugador, tema: this.tema }); }
  in(entrada: unknown): void { void entrada; }
}

class IntentarAdivinarEnviable extends Enviable {
  constructor(private readonly jugadorId: string, private readonly nombreJugador: string, private readonly intento: string) { super(); }
  out(): string { return JSON.stringify({ comando: 'INTENTAR_ADIVINAR', jugadorId: this.jugadorId, nombreJugador: this.nombreJugador, intento: this.intento }); }
  in(entrada: unknown): void { void entrada; }
}

class AddStrokeEnviable extends Enviable {
  constructor(
    private readonly jugadorId: string,
    private readonly x: number,
    private readonly y: number,
    private readonly t: number,
    private readonly down: boolean
  ) {
    super();
  }
  out(): string {
    return JSON.stringify({
      comando: 'ADD_STROKE',
      jugadorId: this.jugadorId,
      x: this.x,
      y: this.y,
      t: this.t,
      down: this.down
    });
  }
  in(entrada: unknown): void { void entrada; }
}

class ComandoVacioEnviable extends Enviable {
  constructor(private readonly jugadorId: string, private readonly comando: string) {
    super();
  }
  out(): string {
    return JSON.stringify({
      comando: this.comando,
      jugadorId: this.jugadorId
    });
  }
  in(entrada: unknown): void { void entrada; }
}
