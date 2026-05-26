import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { obtenerApiBaseUrl } from '../../core/config/api-base';

export interface HistorialResultado {
  nombreJugador: string;
  puntuacion: number;
  posicion?: number | null;
  victoria: boolean;
}

export interface HistorialJuego {
  orden: number;
  fechaJugado: string;
  juegoNombre: string;
  juegoRuta: string;
  puntuacionTotal: number;
  jugadores: HistorialResultado[];
  ganadores: string[];
}

export interface HistorialSala {
  uuid: string;
  hostNombre: string;
  creadaEn: string;
  juegosJugados: HistorialJuego[];
}

@Injectable({
  providedIn: 'root',
})
export class HistorialService {
  private readonly apiBase = obtenerApiBaseUrl();
  private readonly requestOptions = { withCredentials: true };

  constructor(private readonly http: HttpClient) {}

  obtenerHistorial(): Observable<HistorialSala[]> {
    return this.http.get<HistorialSala[]>(`${this.apiBase}/api/historial`, this.requestOptions);
  }
}