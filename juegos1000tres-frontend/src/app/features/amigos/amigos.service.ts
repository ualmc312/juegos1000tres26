import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { obtenerApiBaseUrl } from '../../core/config/api-base';

export interface Usuario {
  id: number;
  nombre: string;
  email: string;
  salaUuid?: string | null;
}

export interface SolicitudAmistad {
  id: number;
  usuarioSolicitante: Usuario;
  usuarioReceptor: Usuario;
  estado: 'PENDIENTE' | 'ACEPTADA' | 'RECHAZADA';
  fechaCreacion: string;
}

export interface Amistad {
  id: number;
  usuario1: Usuario;
  usuario2: Usuario;
  fechaCreacion: string;
}

export interface SalaRespuesta {
  uuid: string;
  jugadores: Array<{ id: string; nombre: string; victorias: number }>;
  hostId: string;
  pantallaId: string;
  juegoActual: string;
  p2pHostPeerId?: string | null;
  jugadorId?: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class AmigosService {
  private readonly apiBase = obtenerApiBaseUrl();
  private readonly requestOptions = { withCredentials: true };

  constructor(private readonly http: HttpClient) {}

  /**
   * Envía una solicitud de amistad
   */
  enviarSolicitud(usuarioSolicitanteId: number, usuarioReceptorId: number): Observable<SolicitudAmistad> {
    return this.http.post<SolicitudAmistad>(
      `${this.apiBase}/api/amigos/solicitar`,
      { usuarioSolicitanteId, usuarioReceptorId },
      this.requestOptions
    );
  }

  /**
   * Acepta una solicitud de amistad
   */
  aceptarSolicitud(solicitudId: number): Observable<Amistad> {
    return this.http.put<Amistad>(
      `${this.apiBase}/api/amigos/aceptar/${solicitudId}`,
      {},
      this.requestOptions
    );
  }

  /**
   * Rechaza una solicitud de amistad
   */
  rechazarSolicitud(solicitudId: number): Observable<SolicitudAmistad> {
    return this.http.put<SolicitudAmistad>(
      `${this.apiBase}/api/amigos/rechazar/${solicitudId}`,
      {},
      this.requestOptions
    );
  }

  /**
   * Elimina una amistad
   */
  eliminarAmistad(usuarioId1: number, usuarioId2: number): Observable<void> {
    return this.http.delete<void>(
      `${this.apiBase}/api/amigos/${usuarioId1}/${usuarioId2}`,
      this.requestOptions
    );
  }

  /**
   * Obtiene la lista de amigos de un usuario
   */
  obtenerAmigos(usuarioId: number): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(
      `${this.apiBase}/api/amigos/mis-amigos/${usuarioId}`,
      this.requestOptions
    );
  }

  /**
   * Obtiene las solicitudes pendientes recibidas
   */
  obtenerSolicitudesRecibidas(usuarioId: number): Observable<SolicitudAmistad[]> {
    return this.http.get<SolicitudAmistad[]>(
      `${this.apiBase}/api/amigos/solicitudes-recibidas/${usuarioId}`,
      this.requestOptions
    );
  }

  /**
   * Obtiene las solicitudes pendientes enviadas
   */
  obtenerSolicitudesEnviadas(usuarioId: number): Observable<SolicitudAmistad[]> {
    return this.http.get<SolicitudAmistad[]>(
      `${this.apiBase}/api/amigos/solicitudes-enviadas/${usuarioId}`,
      this.requestOptions
    );
  }

  /**
   * Verifica si dos usuarios son amigos
   */
  sonAmigos(usuarioId1: number, usuarioId2: number): Observable<{ sonAmigos: boolean }> {
    return this.http.get<{ sonAmigos: boolean }>(
      `${this.apiBase}/api/amigos/son-amigos/${usuarioId1}/${usuarioId2}`,
      this.requestOptions
    );
  }

  /**
   * Busca un usuario por email
   */
  buscarPorEmail(email: string): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(
      `${this.apiBase}/api/amigos/buscar?email=${encodeURIComponent(email)}`,
      this.requestOptions
    );
  }

  /**
   * Se une a la sala activa de un amigo
   */
  unirseASala(uuid: string): Observable<SalaRespuesta> {
    return this.http.get<SalaRespuesta>(
      `${this.apiBase}/sala/${uuid}/unirse`,
      this.requestOptions
    );
  }
}
