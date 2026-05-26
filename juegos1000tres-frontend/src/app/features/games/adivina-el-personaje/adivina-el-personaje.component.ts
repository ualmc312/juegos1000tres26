import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Enviable, Envio, Recibo, Traductor, WebSocketConexion } from '../../../core/comunicacion';

type JugadorEstado = {
  jugadorId: string;
  nombreJugador: string;
  preguntasValidas: number;
  preguntasInvalidas: number;
  preguntasAlAcertar: number;
  acertado: boolean;
};

type InteraccionEstado = {
  tipo: string;
  jugadorId: string;
  contenido: string;
  respuestaIA: string;
  valida: boolean;
};

type EstadoAdivina = {
  comando: string;
  fase: string;
  enCurso: boolean;
  tema: string;
  mensaje: string;
  ultimaRespuestaIA: string;
  jugadorTemaId?: string;
  personajeRevelado: string;
  descripcionPersonaje: string;
  ganadores: string[];
  ganadoresNombres?: string[];
  jugadores: JugadorEstado[];
  historial: InteraccionEstado[];
  puedeProponerTema: boolean;
  puedePreguntar: boolean;
  puedeAdivinar: boolean;
};

@Component({
  selector: 'app-adivina-el-personaje',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './adivina-el-personaje.component.html',
  styleUrls: ['./adivina-el-personaje.component.css'],
})
export class AdivinaElPersonajeComponent implements OnInit, OnDestroy {
  @Input() uuid = '';
  @Input() jugadorId = '';
  @Input() pantallaId = '';
  @Input() esPantalla = false;
  @Input() esHost = false;
  @Output() volverSala = new EventEmitter<void>();

  nombreJugador = 'Jugador';
  tema = '';
  pregunta = '';
  intento = '';
  estadoConexion = 'Preparando WebSocket...';
  estado: Partial<EstadoAdivina> = {};

  private traductor?: Traductor<string>;
  private recepcionActiva = false;
  private registroEnviado = false;
  private jugadorPersistente = '';

  ngOnInit(): void {
    this.nombreJugador = this.nombreInicial();
    this.jugadorPersistente = this.obtenerJugadorPersistente();
    this.inicializarComunicacion();
    this.iniciarRecepcion();
  }

  ngOnDestroy(): void {
    this.recepcionActiva = false;
    this.traductor?.desconectar();
    this.traductor = undefined;
  }

  proponerTema(): void {
    const temaLimpio = this.tema.trim();
    if (!temaLimpio || !this.traductor) {
      return;
    }

    this.traductor.enviar(new ProponerTemaEnviable(this.idJugadorActual, this.nombreJugador, temaLimpio));
    this.tema = '';
  }

  enviarPregunta(): void {
    const preguntaLimpia = this.pregunta.trim();
    if (!preguntaLimpia || !this.traductor) {
      return;
    }

    this.traductor.enviar(new HacerPreguntaEnviable(this.idJugadorActual, this.nombreJugador, preguntaLimpia));
    this.pregunta = '';
  }

  intentarAdivinar(): void {
    const intentoLimpio = this.intento.trim();
    if (!intentoLimpio || !this.traductor) {
      return;
    }

    this.traductor.enviar(new IntentarAdivinarEnviable(this.idJugadorActual, this.nombreJugador, intentoLimpio));
    this.intento = '';
  }

  get jugadoresOrdenados(): JugadorEstado[] {
    return [...(this.estado.jugadores || [])].sort((a, b) => {
      const nombreA = a.nombreJugador || '';
      const nombreB = b.nombreJugador || '';
      return nombreA.localeCompare(nombreB);
    });
  }

  get historialReciente(): InteraccionEstado[] {
    return [...(this.estado.historial || [])].slice(-20);
  }

  get soyJugadorTema(): boolean {
    return !!this.estado.jugadorTemaId && this.estado.jugadorTemaId === this.idJugadorActual;
  }

  get esFaseFinalizada(): boolean {
    return this.estado.fase === 'FINALIZADA';
  }

  esGanador(jugador: JugadorEstado): boolean {
    const ganadores = this.estado.ganadores || [];
    return this.esFaseFinalizada
      && (ganadores.includes(jugador.jugadorId) || ganadores.includes(jugador.nombreJugador));
  }

  volverALaSala(): void {
    if (!this.esHost || !this.esFaseFinalizada) {
      return;
    }

    this.volverSala.emit();
  }

  private inicializarComunicacion(): void {
    const salaId = this.salaId();
    const rol = this.esPantalla ? 'pantalla' : 'jugadores';
    let canal = `ws://127.0.0.1:8091/ws/salas/${encodeURIComponent(salaId)}/${rol}`;
    if (rol === 'jugadores') {
      const id = encodeURIComponent(this.idJugadorActual || '');
      if (id) {
        canal = `${canal}?jugadorId=${id}`;
      }
    }

    const conexion = new WebSocketConexion(salaId, canal);
    const envio = Envio.paraStringDesdeOut();
    const recibo = new Recibo(String, Recibo.extractorComandoDesdeJson())
      .conEvento('ESTADO_ADIVINA_PERSONAJE', {
        hacer: (payload: string) => this.procesarEstado(payload),
      });

    this.traductor = new Traductor(conexion, envio, recibo);
    this.traductor.conectar();
    this.estadoConexion = `Conectando a ${rol} de ${salaId}...`;
  }

  private iniciarRecepcion(): void {
    this.recepcionActiva = true;
    void this.bucleRecepcion();
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
        if (!this.recepcionActiva) {
          break;
        }

        this.estadoConexion = `Conexion inestable: ${this.formatearError(error)}`;
        await this.esperar(300);
      }
    }
  }

  private procesarEstado(payload: string): void {
    try {
      const data = JSON.parse(payload) as Record<string, unknown>;
      if (data['comando'] !== 'ESTADO_ADIVINA_PERSONAJE') {
        return;
      }

      this.estado = {
        comando: String(data['comando'] ?? ''),
        fase: String(data['fase'] ?? ''),
        enCurso: Boolean(data['enCurso']),
        tema: String(data['tema'] ?? ''),
        mensaje: String(data['mensaje'] ?? ''),
        ultimaRespuestaIA: String(data['ultimaRespuestaIA'] ?? ''),
        jugadorTemaId: typeof data['jugadorTemaId'] === 'string' ? String(data['jugadorTemaId']) : undefined,
        personajeRevelado: String(data['personajeRevelado'] ?? ''),
        descripcionPersonaje: String(data['descripcionPersonaje'] ?? ''),
        ganadores: Array.isArray(data['ganadores']) ? data['ganadores'].map((item) => String(item)) : [],
        ganadoresNombres: Array.isArray(data['ganadoresNombres']) ? data['ganadoresNombres'].map((item) => String(item)) : [],
        jugadores: Array.isArray(data['jugadores'])
          ? data['jugadores'].map((item) => {
              const registro = item as Record<string, unknown>;
              return {
                jugadorId: String(registro['jugadorId'] ?? ''),
                nombreJugador: String(registro['nombreJugador'] ?? 'Jugador'),
                preguntasValidas: Number(registro['preguntasValidas'] ?? 0),
                preguntasInvalidas: Number(registro['preguntasInvalidas'] ?? 0),
                preguntasAlAcertar: Number(registro['preguntasAlAcertar'] ?? 0),
                acertado: Boolean(registro['acertado']),
              };
            })
          : [],
        historial: Array.isArray(data['historial'])
          ? data['historial'].map((item) => {
              const registro = item as Record<string, unknown>;
              return {
                tipo: String(registro['tipo'] ?? ''),
                jugadorId: String(registro['jugadorId'] ?? ''),
                contenido: String(registro['contenido'] ?? ''),
                respuestaIA: String(registro['respuestaIA'] ?? ''),
                valida: Boolean(registro['valida']),
              };
            })
          : [],
        puedeProponerTema: Boolean(data['puedeProponerTema']),
        puedePreguntar: Boolean(data['puedePreguntar']),
        puedeAdivinar: Boolean(data['puedeAdivinar']),
      };
    } catch {
      // Ignorar payloads que no correspondan al estado del juego.
    }
  }

  private registrarJugadorSiHaceFalta(): void {
    if (this.esPantalla || this.registroEnviado || !this.traductor) {
      return;
    }

    this.traductor.enviar(new RegistrarJugadorEnviable(this.idJugadorActual, this.nombreJugador));
    this.registroEnviado = true;
  }

  private obtenerJugadorPersistente(): string {
    const clave = this.claveStorageJugador();
    const actual = localStorage.getItem(clave);

    if (actual && actual.trim()) {
      return actual.trim();
    }

    const nuevo = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `jug-${Date.now()}-${Math.floor(Math.random() * 99999)}`;

    localStorage.setItem(clave, nuevo);
    return nuevo;
  }

  private nombreInicial(): string {
    const clave = this.claveStorageNombre();
    const actual = localStorage.getItem(clave);
    if (actual && actual.trim()) {
      return actual.trim();
    }

    return 'Jugador';
  }

  private salaId(): string {
    return this.uuid?.trim() || 'adivina-el-personaje';
  }

  private claveStorageJugador(): string {
    return `adivina_personaje_jugador_id:${this.salaId()}`;
  }

  private get idJugadorActual(): string {
    return this.jugadorId?.trim() || this.jugadorPersistente;
  }

  private claveStorageNombre(): string {
    return `adivina_personaje_nombre:${this.salaId()}`;
  }

  private formatearError(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }

    return String(error);
  }

  private obtenerConexionWebSocket(): WebSocketConexion | undefined {
    const conexion = this.traductor?.getConexion();
    return conexion instanceof WebSocketConexion ? conexion : undefined;
  }

  private esperar(ms: number): Promise<void> {
    return new Promise((resolve) => window.setTimeout(resolve, ms));
  }
}

class RegistrarJugadorEnviable extends Enviable {
  constructor(
    private readonly jugadorId: string,
    private readonly nombreJugador: string
  ) {
    super();
  }

  out(): string {
    return JSON.stringify({
      comando: 'REGISTRAR_JUGADOR',
      jugadorId: this.jugadorId,
      nombreJugador: this.nombreJugador,
    });
  }

  in(entrada: unknown): void {
    void entrada;
  }
}

class ProponerTemaEnviable extends Enviable {
  constructor(
    private readonly jugadorId: string,
    private readonly nombreJugador: string,
    private readonly tema: string
  ) {
    super();
  }

  out(): string {
    return JSON.stringify({
      comando: 'PROPONER_TEMA',
      jugadorId: this.jugadorId,
      nombreJugador: this.nombreJugador,
      tema: this.tema,
    });
  }

  in(entrada: unknown): void {
    void entrada;
  }
}

class HacerPreguntaEnviable extends Enviable {
  constructor(
    private readonly jugadorId: string,
    private readonly nombreJugador: string,
    private readonly pregunta: string
  ) {
    super();
  }

  out(): string {
    return JSON.stringify({
      comando: 'HACER_PREGUNTA',
      jugadorId: this.jugadorId,
      nombreJugador: this.nombreJugador,
      pregunta: this.pregunta,
    });
  }

  in(entrada: unknown): void {
    void entrada;
  }
}

class IntentarAdivinarEnviable extends Enviable {
  constructor(
    private readonly jugadorId: string,
    private readonly nombreJugador: string,
    private readonly intento: string
  ) {
    super();
  }

  out(): string {
    return JSON.stringify({
      comando: 'INTENTAR_ADIVINAR',
      jugadorId: this.jugadorId,
      nombreJugador: this.nombreJugador,
      intento: this.intento,
    });
  }

  in(entrada: unknown): void {
    void entrada;
  }
}
