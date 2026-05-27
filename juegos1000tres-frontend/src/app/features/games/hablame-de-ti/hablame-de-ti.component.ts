import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Enviable, Envio, Recibo, Traductor, WebSocketConexion } from '../../../core/comunicacion';

type PreguntaAsignada = {
  jugadorId: string;
  nombreJugador: string;
  preguntaDirecta: string;
  preguntaPublica: string;
  respondida: boolean;
  respuestaOriginal: string;
};

type OpcionRespuesta = {
  opcionId: string;
  autorJugadorId: string;
  autorNombre: string;
  texto: string;
  esOriginal: boolean;
  seleccionable: boolean;
};

type Marcador = {
  jugadorId: string;
  nombreJugador: string;
  puntos: number;
};

type ResumenRonda = {
  respuesta: string;
  esOriginal: boolean;
  autorJugadorId: string;
  autorNombre: string;
  numSeleccionados: number;
  puntosGanadosEstaRonda: number;
  puntosTotales: number;
};

type EstadoHablameDeTi = {
  comando: string;
  fase: string;
  enCurso: boolean;
  rondaActual: number;
  totalRondas: number;
  mensaje: string;
  ultimoError: string;
  jugadorObjetivo?: { jugadorId: string; nombreJugador: string };
  preguntaDirecta: string;
  preguntaPublica: string;
  respuestaOriginal: string;
  ganadores: string[];
  ganadoresNombres?: string[];
  preguntasAsignadas: PreguntaAsignada[];
  mentirasPendientes: string[];
  votosPendientes: string[];
  opciones: OpcionRespuesta[];
  resumenRonda: ResumenRonda[];
  marcador: Marcador[];
  puedeEmpezar: boolean;
  puedeResponder: boolean;
  puedeMentir: boolean;
  puedeVotar: boolean;
  puedeContinuar: boolean;
};

@Component({
  selector: 'app-hablame-de-ti',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './hablame-de-ti.component.html',
  styleUrls: ['./hablame-de-ti.component.css'],
})
export class HablameDeTiComponent implements OnInit, OnDestroy {
  @Input() uuid = '';
  @Input() jugadorId = '';
  @Input() pantallaId = '';
  @Input() esPantalla = false;
  @Input() esHost = false;
  @Output() volverSala = new EventEmitter<void>();

  nombreJugador = 'Jugador';
  respuesta = '';
  mentira = '';
  opcionSeleccionada = '';
  estadoConexion = 'Preparando WebSocket...';
  estado: Partial<EstadoHablameDeTi> = {};

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

  iniciarPartida(): void {
    if (!this.traductor || !this.esHost) {
      return;
    }

    this.traductor.enviar(new IniciarPartidaEnviable(this.idJugadorActual));
  }

  responderPregunta(): void {
    const texto = this.respuesta.trim();
    if (!texto || !this.traductor) {
      return;
    }

    this.traductor.enviar(new ResponderPreguntaEnviable(this.idJugadorActual, this.nombreJugador, texto));
    this.respuesta = '';
  }

  enviarMentira(): void {
    const texto = this.mentira.trim();
    if (!texto || !this.traductor) {
      return;
    }

    this.traductor.enviar(new EnviarMentiraEnviable(this.idJugadorActual, this.nombreJugador, texto));
    this.mentira = '';
  }

  votarRespuesta(): void {
    const opcionId = this.opcionSeleccionada.trim();
    if (!opcionId || !this.traductor) {
      return;
    }

    this.traductor.enviar(new VotarRespuestaEnviable(this.idJugadorActual, opcionId));
    this.opcionSeleccionada = '';
  }

  get miPregunta(): PreguntaAsignada | undefined {
    return (this.estado.preguntasAsignadas || []).find((pregunta) => pregunta.jugadorId === this.idJugadorActual);
  }

  get soyJugadorObjetivo(): boolean {
    return this.estado.jugadorObjetivo?.jugadorId === this.jugadorId;
  }

  get preguntasPendientesTexto(): string {
    const mentirasPendientes = this.estado.mentirasPendientes ?? [];
    return mentirasPendientes.length ? mentirasPendientes.join(', ') : 'Nadie';
  }

  get votosPendientesTexto(): string {
    const votosPendientes = this.estado.votosPendientes ?? [];
    return votosPendientes.length ? votosPendientes.join(', ') : 'Nadie';
  }

  get opcionesParaVotar(): OpcionRespuesta[] {
    return (this.estado.opciones || []).filter((opcion) => opcion.esOriginal || opcion.autorJugadorId !== this.idJugadorActual);
  }

  get muestraSoloFondoEstatico(): boolean {
    return this.estado.fase === 'RESPONDIENDO_ORIGINALES';
  }

  get esFaseFinalizada(): boolean {
    return this.estado.fase === 'FINALIZADA';
  }

  esGanador(jugador: Marcador): boolean {
    const ganadores = this.estado.ganadores || [];
    const ganadoresNombres = this.estado.ganadoresNombres || [];

    return this.esFaseFinalizada
      && (ganadores.includes(jugador.jugadorId) || ganadoresNombres.includes(jugador.nombreJugador));
  }

  get ganadoresTexto(): string {
    const ganadoresNombres = this.estado.ganadoresNombres || [];
    if (ganadoresNombres.length) {
      return ganadoresNombres.join(', ');
    }

    return (this.estado.ganadores || []).join(', ');
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
    const canal = `ws://127.0.0.1:8091/ws/salas/${encodeURIComponent(salaId)}/${rol}`;

    const conexion = new WebSocketConexion(salaId, canal);
    const envio = Envio.paraStringDesdeOut();
    const recibo = new Recibo(String, Recibo.extractorComandoDesdeJson())
      .conEvento('ESTADO_HABLAME_DE_TI', {
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
      if (data['comando'] !== 'ESTADO_HABLAME_DE_TI') {
        return;
      }

      this.estado = {
        comando: String(data['comando'] ?? ''),
        fase: String(data['fase'] ?? ''),
        enCurso: Boolean(data['enCurso']),
        rondaActual: Number(data['rondaActual'] ?? 0),
        totalRondas: Number(data['totalRondas'] ?? 0),
        mensaje: String(data['mensaje'] ?? ''),
        ultimoError: String(data['ultimoError'] ?? ''),
        jugadorObjetivo: data['jugadorObjetivo'] && typeof data['jugadorObjetivo'] === 'object'
          ? {
              jugadorId: String((data['jugadorObjetivo'] as Record<string, unknown>)['jugadorId'] ?? ''),
              nombreJugador: String((data['jugadorObjetivo'] as Record<string, unknown>)['nombreJugador'] ?? ''),
            }
          : undefined,
        preguntaDirecta: String(data['preguntaDirecta'] ?? ''),
        preguntaPublica: String(data['preguntaPublica'] ?? ''),
        respuestaOriginal: String(data['respuestaOriginal'] ?? ''),
        ganadores: Array.isArray(data['ganadores']) ? data['ganadores'].map((item) => String(item)) : [],
        ganadoresNombres: Array.isArray(data['ganadoresNombres']) ? data['ganadoresNombres'].map((item) => String(item)) : [],
        preguntasAsignadas: Array.isArray(data['preguntasAsignadas'])
          ? data['preguntasAsignadas'].map((item) => {
              const registro = item as Record<string, unknown>;
              return {
                jugadorId: String(registro['jugadorId'] ?? ''),
                nombreJugador: String(registro['nombreJugador'] ?? 'Jugador'),
                preguntaDirecta: String(registro['preguntaDirecta'] ?? ''),
                preguntaPublica: String(registro['preguntaPublica'] ?? ''),
                respondida: Boolean(registro['respondida']),
                respuestaOriginal: String(registro['respuestaOriginal'] ?? ''),
              };
            })
          : [],
        mentirasPendientes: Array.isArray(data['mentirasPendientes']) ? data['mentirasPendientes'].map((item) => String(item)) : [],
        votosPendientes: Array.isArray(data['votosPendientes']) ? data['votosPendientes'].map((item) => String(item)) : [],
        opciones: Array.isArray(data['opciones'])
          ? data['opciones'].map((item) => {
              const registro = item as Record<string, unknown>;
              return {
                opcionId: String(registro['opcionId'] ?? ''),
                autorJugadorId: String(registro['autorJugadorId'] ?? ''),
                autorNombre: String(registro['autorNombre'] ?? 'Jugador'),
                texto: String(registro['texto'] ?? ''),
                esOriginal: Boolean(registro['esOriginal']),
                seleccionable: Boolean(registro['seleccionable']),
              };
            })
          : [],
        resumenRonda: Array.isArray(data['resumenRonda'])
          ? data['resumenRonda'].map((item) => {
              const registro = item as Record<string, unknown>;
              return {
                respuesta: String(registro['respuesta'] ?? ''),
                esOriginal: Boolean(registro['esOriginal']),
                autorJugadorId: String(registro['autorJugadorId'] ?? ''),
                autorNombre: String(registro['autorNombre'] ?? 'Jugador'),
                numSeleccionados: Number(registro['numSeleccionados'] ?? 0),
                puntosGanadosEstaRonda: Number(registro['puntosGanadosEstaRonda'] ?? 0),
                puntosTotales: Number(registro['puntosTotales'] ?? 0),
              };
            })
          : [],
        marcador: Array.isArray(data['marcador'])
          ? data['marcador'].map((item) => {
              const registro = item as Record<string, unknown>;
              return {
                jugadorId: String(registro['jugadorId'] ?? ''),
                nombreJugador: String(registro['nombreJugador'] ?? 'Jugador'),
                puntos: Number(registro['puntos'] ?? 0),
              };
            })
          : [],
        puedeEmpezar: Boolean(data['puedeEmpezar']),
        puedeResponder: Boolean(data['puedeResponder']),
        puedeMentir: Boolean(data['puedeMentir']),
        puedeVotar: Boolean(data['puedeVotar']),
        puedeContinuar: Boolean(data['puedeContinuar']),
      };
    } catch {
      // Ignorar payloads que no correspondan al estado del juego.
    }
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
    return this.uuid?.trim() || 'hablame-de-ti';
  }

  private claveStorageJugador(): string {
    return `hablame_de_ti_jugador_id:${this.salaId()}`;
  }

  private claveStorageNombre(): string {
    return `hablame_de_ti_nombre:${this.salaId()}`;
  }

  private get idJugadorActual(): string {
    return this.jugadorId?.trim() || this.jugadorPersistente;
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

class IniciarPartidaEnviable extends Enviable {
  constructor(private readonly actorId: string) {
    super();
  }

  out(): string {
    return JSON.stringify({
      comando: 'INICIAR_PARTIDA',
      actorId: this.actorId,
    });
  }

  in(entrada: unknown): void {
    void entrada;
  }
}

class ResponderPreguntaEnviable extends Enviable {
  constructor(
    private readonly jugadorId: string,
    private readonly nombreJugador: string,
    private readonly respuesta: string
  ) {
    super();
  }

  out(): string {
    return JSON.stringify({
      comando: 'RESPONDER_PREGUNTA',
      jugadorId: this.jugadorId,
      nombreJugador: this.nombreJugador,
      respuesta: this.respuesta,
    });
  }

  in(entrada: unknown): void {
    void entrada;
  }
}

class EnviarMentiraEnviable extends Enviable {
  constructor(
    private readonly jugadorId: string,
    private readonly nombreJugador: string,
    private readonly respuesta: string
  ) {
    super();
  }

  out(): string {
    return JSON.stringify({
      comando: 'ENVIAR_MENTIRA',
      jugadorId: this.jugadorId,
      nombreJugador: this.nombreJugador,
      respuesta: this.respuesta,
    });
  }

  in(entrada: unknown): void {
    void entrada;
  }
}

class VotarRespuestaEnviable extends Enviable {
  constructor(
    private readonly jugadorId: string,
    private readonly opcionId: string
  ) {
    super();
  }

  out(): string {
    return JSON.stringify({
      comando: 'VOTAR_RESPUESTA',
      jugadorId: this.jugadorId,
      opcionId: this.opcionId,
    });
  }

  in(entrada: unknown): void {
    void entrada;
  }
}
