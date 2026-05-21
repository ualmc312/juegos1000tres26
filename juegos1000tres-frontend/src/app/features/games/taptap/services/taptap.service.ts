import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { ApiConexion, Envio, Recibo, Traductor } from '../../../../core/comunicacion';
import { TapTapEstadoEnviable, TapTapEstadoData } from '../comunicacion/taptap-estado-enviable';
import { TapTapEstadoEvento } from '../comunicacion/taptap-estado-evento';

export interface TapTapPuntuacion {
  jugadorId: string;
  nombre: string;
  puntos: number;
}

export interface TapTapEstado {
  inicioEpochMs: number;
  duracionMs: number;
  restanteMs: number;
  finalizada: boolean;
  ganadorId?: string | null;
  puntuaciones: TapTapPuntuacion[];
}

export interface TapTapPuntoRespuesta {
  puntos: number;
}

export interface TapTapFinalRespuesta {
  ganadorId?: string | null;
  victoriaRegistrada: boolean;
}

@Injectable({ providedIn: 'root' })
export class TapTapService {
  private readonly baseApi = 'http://localhost:8083';
  private traductor: Traductor<string> | null = null;
  private salaActual = '';
  private estadoActual$ = new BehaviorSubject<Partial<TapTapEstado>>({});

  getEstado$(): Observable<Partial<TapTapEstado>> {
    return this.estadoActual$.asObservable();
  }

  private inicializarIfNeeded(uuid: string): void {
    if (this.traductor && this.salaActual === uuid) {
      return;
    }

    this.desconectar();
    this.salaActual = uuid;

    const conexion = new ApiConexion(this.salaActual, this.baseApi, {}, 'taptap');

    const envio = Envio.paraStringDesdeOut();
    const recibo = new Recibo(String, Recibo.extractorComandoDesdeJson())
      .conEvento('ESTADO', new TapTapEstadoEvento((enviable) => {
        const estado = enviable.getEstado() as Partial<TapTapEstado>;
        this.estadoActual$.next(estado);
      }))
      .conEvento('ESTADO_TAPTAP', new TapTapEstadoEvento((enviable) => {
        const estado = enviable.getEstado() as Partial<TapTapEstado>;
        this.estadoActual$.next(estado);
      }));

    this.traductor = new Traductor(conexion, envio, recibo);
    this.traductor.conectar();
  }

  registrarPunto(uuid: string, jugadorId: string): Observable<TapTapPuntoRespuesta> {
    this.inicializarIfNeeded(uuid);

    const payload = JSON.stringify({ comando: 'REGISTRAR_PUNTO', jugadorId });
    try {
      this.traductor!.enviarPayload(payload);
      return new Observable<TapTapPuntoRespuesta>((obs) => {
        void Promise.resolve(this.traductor!.recibirPayload())
          .then((p) => {
            if (typeof p === 'string' && p.trim()) {
              this.traductor!.procesar(p);
            }
            const estado = this.estadoActual$.getValue() as Partial<TapTapEstado>;
            const puntos = estado.puntuaciones?.find((e) => e.jugadorId === jugadorId)?.puntos ?? 0;
            obs.next({ puntos });
            obs.complete();
          })
          .catch((err) => obs.error(err));
      });
    } catch (err) {
      return new Observable<TapTapPuntoRespuesta>((obs) => obs.error(err));
    }
  }

  finalizarPartida(uuid: string, actorId: string): Observable<TapTapFinalRespuesta> {
    this.inicializarIfNeeded(uuid);

    const payload = JSON.stringify({ comando: 'FINALIZAR', actorId });
    try {
      this.traductor!.enviarPayload(payload);
      return new Observable<TapTapFinalRespuesta>((obs) => {
        void Promise.resolve(this.traductor!.recibirPayload())
          .then((p) => {
            if (typeof p === 'string' && p.trim()) {
              this.traductor!.procesar(p);
            }
            const estado = this.estadoActual$.getValue() as Partial<TapTapEstado>;
            obs.next({ ganadorId: estado.ganadorId ?? null, victoriaRegistrada: !!estado.finalizada });
            obs.complete();
          })
          .catch((err) => obs.error(err));
      });
    } catch (err) {
      return new Observable<TapTapFinalRespuesta>((obs) => obs.error(err));
    }
  }

  obtenerEstado(uuid: string): Observable<TapTapEstado> {
    this.inicializarIfNeeded(uuid);

    return new Observable<TapTapEstado>((obs) => {
      void Promise.resolve(this.traductor!.recibirPayload())
        .then((p) => {
          if (typeof p === 'string' && p.trim()) {
            this.traductor!.procesar(p);
          }
          const estado = this.estadoActual$.getValue() as TapTapEstado;
          obs.next(estado);
          obs.complete();
        })
        .catch((err) => obs.error(err));
    });
  }

  desconectar(): void {
    this.traductor?.desconectar();
    this.traductor = null;
    this.salaActual = '';
    this.estadoActual$.next({});
  }
}
