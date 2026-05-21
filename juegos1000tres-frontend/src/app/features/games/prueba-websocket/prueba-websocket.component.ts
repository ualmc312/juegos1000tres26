import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Enviable, Envio, Recibo, Traductor, WebSocketConexion } from '../../../core/comunicacion';

type JugadorEstado = {
  jugadorId: string;
  nombreJugador: string;
  palabras: string[];
};

@Component({
  selector: 'app-prueba-websocket',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './prueba-websocket.component.html',
  styleUrls: ['./prueba-websocket.component.css'],
})
export class PruebaWebSocketComponent implements OnInit, OnDestroy {
  @Input() uuid = '';
  @Input() jugadorId = '';
  @Input() pantallaId = '';
  @Input() esPantalla = false;
  @Input() esHost = false;
  @Output() volverSala = new EventEmitter<void>();

  nombreJugador = 'Jugador';
  texto = '';
  estadoConexion = 'Preparando WebSocket...';
  mensajes: string[] = [];
  jugadores: JugadorEstado[] = [];

  private traductor?: Traductor<string>;
  private recepcionActiva = false;
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

  enviarTexto(): void {
    const textoLimpio = this.texto.trim();

    if (!textoLimpio) {
      return;
    }

    if (!this.traductor) {
      this.estadoConexion = 'La conexión todavía no está lista';
      return;
    }

    const nombre = this.nombreJugador.trim() || 'Jugador';
    localStorage.setItem(this.claveStorageNombre(), nombre);

    const enviable = new EnviarTextoEnviable(this.jugadorPersistente, nombre, textoLimpio);
    this.traductor.enviar(enviable);
    this.texto = '';
    this.estadoConexion = 'Texto enviado';
  }

  esVistaPantalla(): boolean {
    return this.esPantalla;
  }

  volverALaSala(): void {
    if (!this.esHost) {
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
      .conEvento('TEXTO_GLOBAL', {
        hacer: (payload: string) => this.procesarTextoGlobal(payload),
      })
      .conEvento('ESTADO_PANTALLA', {
        hacer: (payload: string) => this.procesarEstadoPantalla(payload),
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

  private procesarTextoGlobal(payload: string): void {
    try {
      const data = JSON.parse(payload) as Record<string, unknown>;
      if (data['comando'] !== 'TEXTO_GLOBAL') {
        return;
      }

      const texto = typeof data['texto'] === 'string' ? data['texto'].trim() : '';
      if (!texto) {
        return;
      }

      this.mensajes = [...this.mensajes, texto].slice(-100);
    } catch {
      // Ignorar payloads no válidos para el historial global.
    }
  }

  private procesarEstadoPantalla(payload: string): void {
    try {
      const data = JSON.parse(payload) as Record<string, unknown>;
      if (data['comando'] !== 'ESTADO_PANTALLA') {
        return;
      }

      const jugadores = Array.isArray(data['jugadores']) ? (data['jugadores'] as unknown[]) : [];
      this.jugadores = jugadores.map((item: unknown) => {
        const registro = item as Record<string, unknown>;
        const palabrasBrutas = Array.isArray(registro['palabras']) ? registro['palabras'] as unknown[] : [];

        return {
          jugadorId: typeof registro['jugadorId'] === 'string' ? String(registro['jugadorId']) : '',
          nombreJugador: typeof registro['nombreJugador'] === 'string' ? String(registro['nombreJugador']) : 'Jugador',
          palabras: palabrasBrutas.map((palabra: unknown) => String(palabra ?? '')),
        };
      });
    } catch {
      // Ignorar payloads no válidos para la pantalla.
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
    return this.uuid?.trim() || 'prueba-websocket';
  }

  private claveStorageJugador(): string {
    return `prueba_ws_jugador_id:${this.salaId()}`;
  }

  private claveStorageNombre(): string {
    return `prueba_ws_nombre:${this.salaId()}`;
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

class EnviarTextoEnviable extends Enviable {
  constructor(
    private readonly jugadorId: string,
    private readonly nombreJugador: string,
    private readonly texto: string
  ) {
    super();
  }

  out(): string {
    return JSON.stringify({
      comando: 'ENVIAR_TEXTO',
      jugadorId: this.jugadorId,
      nombreJugador: this.nombreJugador,
      texto: this.texto,
    });
  }

  in(entrada: unknown): void {
    void entrada;
  }
}
