import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { ApiConexion, Envio, Recibo, Traductor } from '../../../core/comunicacion';
import { obtenerApiBaseUrl } from '../../../core/config/api-base';
import { PreguntasEstadoEnviable, PreguntasEstadoEvento } from './comunicacion';
import { EstadoPreguntas } from './modelos';

@Injectable()
export class PreguntasService {
  private readonly baseApi = obtenerApiBaseUrl();
  private traductor: Traductor<string> | null = null;
  private salaActual = '';
  private estadoActual$ = new BehaviorSubject<Partial<EstadoPreguntas>>({});
  private sincronizacionHandle: ReturnType<typeof setInterval> | null = null;

  getEstado$(): Observable<Partial<EstadoPreguntas>> {
    return this.estadoActual$.asObservable();
  }

  inicializar(uuid: string): void {
    if (!uuid || uuid.trim() === '') {
      throw new Error('El uuid de la sala es obligatorio');
    }

    this.desconectar();
    this.salaActual = uuid.trim();

    const conexion = new ApiConexion(this.salaActual, this.baseApi, {}, 'preguntas');

    const envio = Envio.paraStringDesdeOut();
    const recibo = new Recibo(String, Recibo.extractorComandoDesdeJson())
      .conEvento('ESTADO_PREGUNTAS', new PreguntasEstadoEvento((estado) => this.estadoActual$.next(estado)))
      .conEvento('ESTADO', new PreguntasEstadoEvento((estado) => this.estadoActual$.next(estado)));

    this.traductor = new Traductor(conexion, envio, recibo);
    this.traductor.conectar();

    this.sincronizarEstado();
    this.iniciarSincronizacionPeriodica();
  }

  registrarJugador(jugadorId: string, nombreJugador: string): void {
    this.enviarComando({
      comando: 'REGISTRAR_JUGADOR',
      jugadorId,
      nombreJugador,
    });
  }

  iniciarRonda(actorId: string): void {
    this.enviarComando({
      comando: 'INICIAR_RONDA',
      actorId,
    });
  }

  actualizarBorrador(texto: string, jugadorId: string): void {
    this.enviarComando({
      comando: 'ACTUALIZAR_BORRADOR',
      texto,
      jugadorId,
    });
  }

  enviarRespuesta(jugadorId: string, respuesta: string): void {
    this.enviarComando({
      comando: 'ENVIAR_RESPUESTA',
      jugadorId,
      respuesta,
    });
  }

  elegirRespuesta(jugadorId: string, opcionId: string): void {
    this.enviarComando({
      comando: 'ELEGIR_RESPUESTA',
      jugadorId,
      opcionId,
    });
  }

  finalizarPartida(actorId: string): void {
    this.enviarComando({
      comando: 'FINALIZAR',
      actorId,
    });
  }

  desconectar(): void {
    if (this.sincronizacionHandle !== null) {
      clearInterval(this.sincronizacionHandle);
      this.sincronizacionHandle = null;
    }

    this.traductor?.desconectar();
    this.traductor = null;
    this.salaActual = '';
  }

  private enviarComando(datos: Record<string, unknown>): void {
    if (!this.traductor) {
      throw new Error('El juego Preguntas no está inicializado');
    }

    this.traductor.enviar(new PreguntasEstadoEnviable(datos));
  }



  private sincronizarEstado(): void {
    if (!this.traductor) {
      return;
    }

    void Promise.resolve(this.traductor.recibirPayload())
      .then((payload) => {
        if (typeof payload === 'string' && payload.trim()) {
          this.traductor?.procesar(payload);
        }
      })
      .catch(() => {
        // el backend puede tardar en publicar el estado inicial
      });
  }

  private iniciarSincronizacionPeriodica(): void {
    if (this.sincronizacionHandle !== null) {
      clearInterval(this.sincronizacionHandle);
    }

    this.sincronizacionHandle = setInterval(() => this.sincronizarEstado(), 1000);
  }
}
