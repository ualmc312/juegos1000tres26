import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { obtenerApiBaseUrl } from '../../core/config/api-base';

export interface Usuario {
  id: number;
  nombre: string;
  email: string;
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
      `${this.apiBase}/amigos/solicitar`,
      { usuarioSolicitanteId, usuarioReceptorId },
      this.requestOptions
    );
  }

  /**
   * Acepta una solicitud de amistad
   */
  aceptarSolicitud(solicitudId: number): Observable<Amistad> {
    return this.http.put<Amistad>(
      `${this.apiBase}/amigos/aceptar/${solicitudId}`,
      {},
      this.requestOptions
    );
  }

  /**
   * Rechaza una solicitud de amistad
   */
  rechazarSolicitud(solicitudId: number): Observable<SolicitudAmistad> {
    return this.http.put<SolicitudAmistad>(
      `${this.apiBase}/amigos/rechazar/${solicitudId}`,
      {},
      this.requestOptions
    );
  }

  /**
   * Elimina una amistad
   */
  eliminarAmistad(usuarioId1: number, usuarioId2: number): Observable<void> {
    return this.http.delete<void>(
      `${this.apiBase}/amigos/${usuarioId1}/${usuarioId2}`,
      this.requestOptions
    );
  }

  /**
   * Obtiene la lista de amigos de un usuario
   */
  obtenerAmigos(usuarioId: number): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(
      `${this.apiBase}/amigos/mis-amigos/${usuarioId}`,
      this.requestOptions
    );
  }

  /**
   * Obtiene las solicitudes pendientes recibidas
   */
  obtenerSolicitudesRecibidas(usuarioId: number): Observable<SolicitudAmistad[]> {
    return this.http.get<SolicitudAmistad[]>(
      `${this.apiBase}/amigos/solicitudes-recibidas/${usuarioId}`,
      this.requestOptions
    );
  }

  /**
   * Obtiene las solicitudes pendientes enviadas
   */
  obtenerSolicitudesEnviadas(usuarioId: number): Observable<SolicitudAmistad[]> {
    return this.http.get<SolicitudAmistad[]>(
      `${this.apiBase}/amigos/solicitudes-enviadas/${usuarioId}`,
      this.requestOptions
    );
  }

  /**
   * Verifica si dos usuarios son amigos
   */
  sonAmigos(usuarioId1: number, usuarioId2: number): Observable<{ sonAmigos: boolean }> {
    return this.http.get<{ sonAmigos: boolean }>(
      `${this.apiBase}/amigos/son-amigos/${usuarioId1}/${usuarioId2}`,
      this.requestOptions
    );
  }

  /**
   * Busca un usuario por email
   */
  buscarPorEmail(email: string): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(
      `${this.apiBase}/amigos/buscar?email=${encodeURIComponent(email)}`,
      this.requestOptions
    );
  }
}
