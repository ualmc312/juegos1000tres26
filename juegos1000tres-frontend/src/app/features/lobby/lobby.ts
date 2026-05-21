import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { obtenerApiBaseUrl } from '../../core/config/api-base';
import { GenericButton } from '../../shared/components/generic-button/generic-button';
import { Taptap } from '../games/taptap/taptap';
import { SpaceInvadersComponent } from '../games/space-invaders/space-invaders.component';
import { PruebaWebSocketComponent } from '../games/prueba-websocket/prueba-websocket.component';
import { PreguntasComponent } from '../games/preguntas/preguntas.component';
import { AdivinaElPersonajeComponent } from '../games/adivina-el-personaje/adivina-el-personaje.component';
import { HablameDeTiComponent } from '../games/hablame-de-ti/hablame-de-ti.component';
import { DibujoComponent } from '../games/dibujo/dibujo.component';

@Component({
  selector: 'app-lobby',
  standalone: true,
  imports: [CommonModule, FormsModule, GenericButton, Taptap, PreguntasComponent, SpaceInvadersComponent, PruebaWebSocketComponent, AdivinaElPersonajeComponent, HablameDeTiComponent, DibujoComponent],
  templateUrl: './lobby.html',
  styleUrl: './lobby.css',
})
export class Lobby implements OnInit, OnDestroy {
  viewMode: 'lobby' | 'sala' = 'lobby';
  uuidInput = '';
  errorUuid = '';

  uuidActual = '';
  jugadorId = '';
  hostId = '';
  p2pHostPeerId = '';
  pantallaId = '';
  juegoActual = '';
  jugadores: JugadorResumen[] = [];
  esHost = false;
  pantallaNingunoId = 'NINGUNO';

  juegosDisponibles = [
    { id: 'space-invaders', nombre: 'Space Invaders' },
    { id: 'prueba-websocket', nombre: 'Prueba WebSocket' },
    { id: 'adivina-el-personaje', nombre: 'Adivina el personaje' },
    { id: 'dibujo', nombre: 'Dibujo' },
    { id: 'hablame-de-ti', nombre: 'Hablame de ti' },
    { id: 'taptap', nombre: 'TapTap' },
    { id: 'preguntas', nombre: 'Preguntas' },
    { id: 'reflejos-p2p', nombre: 'Reflejos P2P' }
  ];

  private readonly apiBase = obtenerApiBaseUrl();
  private readonly requestOptions = { withCredentials: true };
  private polling?: Subscription;
  private ultimoJuegoReflejosAbierto = '';

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const uuid = params.get('uuid');

      this.detenerPolling();
      this.errorUuid = '';

      if (uuid) {
        this.viewMode = 'sala';
        this.uuidActual = uuid;
        this.jugadorId = sessionStorage.getItem('sala.jugadorId') || '';
        this.hostId = sessionStorage.getItem('sala.hostId') || '';
        this.p2pHostPeerId = sessionStorage.getItem('sala.p2pHostPeerId') || '';
        this.actualizarEstado();
        this.iniciarPolling();
        return;
      }

      this.viewMode = 'lobby';
    });
  }

  ngOnDestroy(): void {
    this.detenerPolling();
  }

  crearSala(): void {
    this.errorUuid = '';

    this.http.get<SalaRespuesta>(`${this.apiBase}/sala/crear`, this.requestOptions).subscribe({
      next: respuesta => this.navegarSala(respuesta),
      error: () => {
        this.errorUuid = 'No se pudo crear la sala';
        this.cdr.detectChanges();
      }
    });
  }

  unirseSala(): void {
    const uuid = this.uuidInput.trim();

    if (!uuid) {
      this.errorUuid = 'Introduce un UUID';
      return;
    }

    this.errorUuid = '';

    this.http.get<SalaRespuesta>(`${this.apiBase}/sala/${uuid}/unirse`, this.requestOptions).subscribe({
      next: respuesta => this.navegarSala(respuesta),
      error: (error: HttpErrorResponse) => {
        if (error.status === 404) {
          this.errorUuid = 'uuid invalido';
          this.cdr.detectChanges();
          return;
        }

        this.errorUuid = 'No se pudo unir a la sala';
        this.cdr.detectChanges();
      }
    });
  }

  cambiarPantalla(jugadorId: string): void {
    if (!this.esHost || !this.uuidActual) {
      return;
    }

    const actorId = this.jugadorId;

    if (!actorId) {
      return;
    }

    this.http
      .post<SalaRespuesta>(
        `${this.apiBase}/sala/${this.uuidActual}/pantalla?actorId=${actorId}&jugadorId=${jugadorId}`,
        null,
        this.requestOptions
      )
      .subscribe({
        next: respuesta => this.actualizarDatos(respuesta),
        error: () => {
          this.errorUuid = 'No se pudo cambiar la pantalla';
          this.cdr.detectChanges();
        }
      });
  }

  iniciarJuego(juegoId: string): void {
    if (!this.esHost || !this.uuidActual) {
      return;
    }

    const actorId = this.jugadorId;

    if (!actorId) {
      return;
    }

    this.http
      .post<SalaRespuesta>(
        `${this.apiBase}/sala/${this.uuidActual}/juego?actorId=${actorId}&juego=${juegoId}`,
        null,
        this.requestOptions
      )
      .subscribe({
        next: respuesta => {
          this.actualizarDatos(respuesta);
          if (juegoId === 'reflejos-p2p') {
            this.abrirVistaReflejosP2P();
          }
        },
        error: () => {
          this.errorUuid = 'No se pudo iniciar el juego';
          this.cdr.detectChanges();
        }
      });
  }

  salir(): void {
    if (!this.uuidActual) {
      return;
    }

    const uuid = this.uuidActual;
    const jugadorId = this.jugadorId;
    const request = this.esHost
      ? this.http.post<void>(`${this.apiBase}/sala/${uuid}/apagar`, null, this.requestOptions)
      : this.http.post<void>(
          `${this.apiBase}/sala/${uuid}/salir?jugadorId=${jugadorId}`,
          null,
          this.requestOptions
        );

    request.subscribe({
      next: () => this.limpiarSesion(),
      error: () => this.limpiarSesion()
    });
  }

  private navegarSala(respuesta: SalaRespuesta): void {
    if (!respuesta.uuid || !respuesta.jugadorId) {
      this.errorUuid = 'No se pudo entrar en la sala';
      return;
    }

    sessionStorage.setItem('sala.jugadorId', respuesta.jugadorId);
    sessionStorage.setItem('sala.hostId', respuesta.hostId);
    sessionStorage.setItem('sala.p2pHostPeerId', respuesta.p2pHostPeerId || '');

    this.router.navigate(['/sala', respuesta.uuid]);
  }

  private actualizarEstado(): void {
    if (!this.uuidActual) {
      return;
    }

    this.http.get<SalaRespuesta>(`${this.apiBase}/sala/${this.uuidActual}/estado`, this.requestOptions).subscribe({
      next: respuesta => this.actualizarDatos(respuesta),
      error: (error: HttpErrorResponse) => {
        if (error.status === 404) {
          this.errorUuid = 'uuid invalido';
          this.cdr.detectChanges();
        }

        this.limpiarSesion();
      }
    });
  }

  private actualizarDatos(respuesta: SalaRespuesta): void {
    this.uuidActual = respuesta.uuid;
    this.jugadores = respuesta.jugadores || [];
    this.hostId = respuesta.hostId;
    this.p2pHostPeerId = respuesta.p2pHostPeerId || this.p2pHostPeerId;
    this.pantallaId = respuesta.pantallaId || '';
    this.juegoActual = respuesta.juegoActual || '';
    this.esHost = !!this.jugadorId && this.jugadorId === this.hostId;
    this.cdr.detectChanges();

    this.abrirVistaReflejosP2PAutomaticamente();
  }

  abrirVistaReflejosP2P(): void {
    if (!this.uuidActual || !this.p2pHostPeerId) {
      return;
    }

    this.ultimoJuegoReflejosAbierto = `${this.uuidActual}:${this.jugadorId}:${this.hostId}`;

    let role = 'player';
    if (this.esHost) {
      role = 'host';
    } else if (this.jugadorId === this.pantallaId) {
      role = 'pantalla';
    }
    
    const url = `/reflejos-p2p/${encodeURIComponent(this.uuidActual)}?role=${role}&hostPeerId=${encodeURIComponent(this.p2pHostPeerId)}&jugadorId=${encodeURIComponent(this.jugadorId)}&popup=true`;
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  obtenerNombreJugador(id: string): string {
    const j = this.jugadores.find(x => x.id === id);
    return j ? j.nombre : 'Jugador';
  }


  private iniciarPolling(): void {
    this.polling = interval(3000).subscribe(() => this.actualizarEstado());
  }

  private detenerPolling(): void {
    if (this.polling) {
      this.polling.unsubscribe();
      this.polling = undefined;
    }
  }

  private limpiarSesion(): void {
    this.detenerPolling();
    sessionStorage.removeItem('sala.jugadorId');
    sessionStorage.removeItem('sala.hostId');
    this.ultimoJuegoReflejosAbierto = '';
    this.router.navigate(['/sala']);
  }

  private abrirVistaReflejosP2PAutomaticamente(): void {
    if (this.juegoActual !== 'reflejos-p2p' || !this.uuidActual) {
      this.ultimoJuegoReflejosAbierto = '';
      return;
    }

    if (this.esHost) {
      return;
    }

    const marcaActual = `${this.uuidActual}:${this.jugadorId}:${this.hostId}`;
    if (this.ultimoJuegoReflejosAbierto === marcaActual) {
      return;
    }

    this.ultimoJuegoReflejosAbierto = marcaActual;
    const role = this.jugadorId === this.pantallaId ? 'pantalla' : 'player';
    this.router.navigate(['/reflejos-p2p', this.uuidActual], {
      queryParams: { role, hostPeerId: this.p2pHostPeerId || '', jugadorId: this.jugadorId },
    });
  }
}

interface JugadorResumen {
  id: string;
  nombre: string;
  victorias: number;
}

interface SalaRespuesta {
  uuid: string;
  jugadores: JugadorResumen[];
  hostId: string;
  p2pHostPeerId: string;
  pantallaId: string;
  juegoActual: string;
  jugadorId?: string;
}
