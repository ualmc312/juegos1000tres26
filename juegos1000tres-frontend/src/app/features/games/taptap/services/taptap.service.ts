import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

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
  private readonly apiBase = 'http://localhost:8083';

  constructor(private readonly http: HttpClient) {}

  obtenerEstado(uuid: string): Observable<TapTapEstado> {
    return this.http.get<TapTapEstado>(`${this.apiBase}/sala/${uuid}/juego/taptap/estado`);
  }

  registrarPunto(uuid: string, jugadorId: string): Observable<TapTapPuntoRespuesta> {
    return this.http.post<TapTapPuntoRespuesta>(
      `${this.apiBase}/sala/${uuid}/juego/taptap/punto?jugadorId=${jugadorId}`,
      null
    );
  }

  finalizarPartida(uuid: string, actorId: string): Observable<TapTapFinalRespuesta> {
    return this.http.post<TapTapFinalRespuesta>(
      `${this.apiBase}/sala/${uuid}/juego/taptap/finalizar?actorId=${actorId}`,
      null
    );
  }
}
