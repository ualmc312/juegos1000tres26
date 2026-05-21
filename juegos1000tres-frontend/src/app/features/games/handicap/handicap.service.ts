import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type HandicapFase = 'SELECCIONANDO' | 'MOSTRANDO_RESULTADO' | 'FINALIZADO';

export interface HandicapJugador {
  id: string;
  nombre: string;
  victorias: number;
}

export interface HandicapEstado {
  fase: HandicapFase;
  jugadores: HandicapJugador[];
  ganadores: HandicapJugador[];
  tiempoRestanteMs: number;
  hostBloqueado: boolean;
}

@Injectable({ providedIn: 'root' })
export class HandicapService {
  private readonly apiBase = 'http://localhost:8083';
  private readonly requestOptions = { withCredentials: true };

  constructor(private readonly http: HttpClient) {}

  obtenerEstado(uuid: string): Observable<HandicapEstado> {
    return this.http.get<HandicapEstado>(
      `${this.apiBase}/sala/${uuid}/juego/handicap/estado`,
      this.requestOptions
    );
  }

  confirmarGanadores(uuid: string, actorId: string, ganadores: string[]): Observable<HandicapEstado> {
    return this.http.post<HandicapEstado>(
      `${this.apiBase}/sala/${uuid}/juego/handicap/confirmar?actorId=${encodeURIComponent(actorId)}`,
      { ganadores: ganadores ?? [] },
      this.requestOptions
    );
  }
}
