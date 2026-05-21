import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { GenericButton } from '../../shared/components/generic-button/generic-button';
import { Taptap } from '../games/taptap/taptap';
import { SpaceInvadersComponent } from '../games/space-invaders/space-invaders.component';
import { PruebaWebSocketComponent } from '../games/prueba-websocket/prueba-websocket.component';
import { PreguntasComponent } from '../games/preguntas/preguntas.component';
import { HandicapComponent } from '../games/handicap/handicap.component';
import { AuthService } from '../auth/services/auth.service';
import { AuthSession } from '../auth/models/auth-session.model';

@Component({
  selector: 'app-lobby',
  imports: [CommonModule, FormsModule, GenericButton, Taptap, PreguntasComponent, SpaceInvadersComponent, PruebaWebSocketComponent, HandicapComponent],
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
  pantallaId = '';
  juegoActual = '';
  jugadores: JugadorResumen[] = [];
  usuarioConectadoNombre = '';
  hostNombre = '';
  esHost = false;
  pantallaNingunoId = 'NINGUNO';
  usuarioActual: AuthSession | null = null;

  juegosDisponibles = [
    { id: 'space-invaders', nombre: 'Space Invaders' },
    { id: 'prueba-websocket', nombre: 'Prueba WebSocket' },
    { id: 'taptap', nombre: 'TapTap' },
    { id: 'preguntas', nombre: 'Preguntas' },
    { id: 'handicap', nombre: 'Handicap' }
  ];

  private readonly apiBase = 'http://localhost:8083';
  private readonly requestOptions = { withCredentials: true };
  private polling?: Subscription;
  private authSessionSub?: Subscription;
  private authSub?: Subscription;
  private jugadoresPorId = new Map<string, JugadorResumen>();

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly cdr: ChangeDetectorRef,
    private readonly auth: AuthService
  ) {}

  ngOnInit(): void {
    this.authSessionSub = this.auth.loadSession().subscribe(user => {
      this.usuarioActual = user;
      this.actualizarNombresClave();
    });
    this.authSub = this.auth.currentUser$.subscribe(user => {
      this.usuarioActual = user;
      this.actualizarNombresClave();
    });

    this.route.paramMap.subscribe(params => {
      const uuid = params.get('uuid');

      this.detenerPolling();
      this.errorUuid = '';

      if (uuid) {
        this.viewMode = 'sala';
        this.uuidActual = uuid;
        this.jugadorId = sessionStorage.getItem('sala.jugadorId') || '';
        this.hostId = sessionStorage.getItem('sala.hostId') || '';
        this.actualizarEstado();
        this.iniciarPolling();
        return;
      }

      this.viewMode = 'lobby';
    });
  }

  ngOnDestroy(): void {
    this.detenerPolling();
    this.authSessionSub?.unsubscribe();
    this.authSub?.unsubscribe();
  }

  crearSala(): void {
    this.errorUuid = '';
    const params = this.crearParamsNombre();

    this.http.get<SalaRespuesta>(`${this.apiBase}/sala/crear`, { ...this.requestOptions, params }).subscribe({
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
    const params = this.crearParamsNombre();

    this.http.get<SalaRespuesta>(`${this.apiBase}/sala/${uuid}/unirse`, { ...this.requestOptions, params }).subscribe({
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
        next: respuesta => this.actualizarDatos(respuesta),
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

  copiarUUID(): void {
    navigator.clipboard.writeText(this.uuidActual).then(() => {
      console.log('UUID copiado al portapapeles:', this.uuidActual);
    }).catch(err => {
      console.error('Error al copiar el UUID:', err);
    });
  }

  private navegarSala(respuesta: SalaRespuesta): void {
    if (!respuesta.uuid || !respuesta.jugadorId) {
      this.errorUuid = 'No se pudo entrar en la sala';
      return;
    }

    sessionStorage.setItem('sala.jugadorId', respuesta.jugadorId);
    sessionStorage.setItem('sala.hostId', respuesta.hostId);

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
    if (respuesta.jugadorId) {
      this.jugadorId = respuesta.jugadorId;
      sessionStorage.setItem('sala.jugadorId', respuesta.jugadorId);
    }
    this.jugadores = respuesta.jugadores || [];
    this.jugadoresPorId = new Map(this.jugadores.map(jugador => [jugador.id, jugador]));
    this.hostId = respuesta.hostId;
    this.pantallaId = respuesta.pantallaId || '';
    this.juegoActual = respuesta.juegoActual || '';
    this.esHost = !!this.jugadorId && this.jugadorId === this.hostId;
    this.actualizarNombresClave();
    this.cdr.detectChanges();
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
    this.router.navigate(['/sala']);
  }

  private crearParamsNombre(): HttpParams | undefined {
    const nombre = this.usuarioActual?.nombre?.trim();

    if (!nombre) {
      return undefined;
    }

    return new HttpParams().set('nombre', nombre);
  }


  private actualizarNombresClave(): void {
    this.usuarioConectadoNombre = this.obtenerNombrePorId(this.jugadorId);
    this.hostNombre = this.obtenerNombrePorId(this.hostId);
  }

  private obtenerNombrePorId(id: string): string {
    if (!id) {
      return '';
    }

    const jugador = this.jugadoresPorId.get(id);
    if (jugador?.nombre) {
      return jugador.nombre;
    }
    return '';
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
  pantallaId: string;
  juegoActual: string;
  jugadorId?: string;
}
