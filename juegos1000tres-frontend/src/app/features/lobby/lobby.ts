import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { GenericButton } from '../../shared/components/generic-button/generic-button';
import { Taptap } from '../games/taptap/taptap';

@Component({
  selector: 'app-lobby',
  imports: [CommonModule, FormsModule, GenericButton, Taptap],
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
  esHost = false;
  pantallaNingunoId = 'NINGUNO';

  juegoUrl?: SafeResourceUrl;
  pantallaUrl?: SafeResourceUrl;
  private juegoUrlRaw = '';
  private pantallaUrlRaw = '';

  juegosDisponibles = [
    { id: 'space-invaders', nombre: 'Space Invaders' },
    { id: 'taptap', nombre: 'TapTap' }
  ];

  private readonly apiBase = 'http://localhost:8083';
  private polling?: Subscription;

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly cdr: ChangeDetectorRef,
    private readonly sanitizer: DomSanitizer
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

    this.http.get<SalaRespuesta>(`${this.apiBase}/sala/crear`).subscribe({
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

    this.http.get<SalaRespuesta>(`${this.apiBase}/sala/${uuid}/unirse`).subscribe({
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
        null
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
        null
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
      ? this.http.post<void>(`${this.apiBase}/sala/${uuid}/apagar`, null)
      : this.http.post<void>(`${this.apiBase}/sala/${uuid}/salir?jugadorId=${jugadorId}`, null);

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

    this.router.navigate(['/sala', respuesta.uuid]);
  }

  private actualizarEstado(): void {
    if (!this.uuidActual) {
      return;
    }

    this.http.get<SalaRespuesta>(`${this.apiBase}/sala/${this.uuidActual}/estado`).subscribe({
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
    this.pantallaId = respuesta.pantallaId || '';
    this.juegoActual = respuesta.juegoActual || '';
    this.esHost = !!this.jugadorId && this.jugadorId === this.hostId;
    this.actualizarUrlsJuego();
    this.cdr.detectChanges();
  }

  private actualizarUrlsJuego(): void {
    if (this.juegoActual !== 'space-invaders' || !this.uuidActual) {
      this.juegoUrl = undefined;
      this.pantallaUrl = undefined;
      this.juegoUrlRaw = '';
      this.pantallaUrlRaw = '';
      return;
    }

    const base = 'http://localhost:5000';
    const pantallaId = this.uuidActual;

    const juegoUrlRaw = `${base}/`;
    const pantallaUrlRaw = `${base}/pantalla?screenId=${encodeURIComponent(pantallaId)}`;

    if (juegoUrlRaw !== this.juegoUrlRaw) {
      this.juegoUrlRaw = juegoUrlRaw;
      this.juegoUrl = this.sanitizer.bypassSecurityTrustResourceUrl(juegoUrlRaw);
    }

    if (pantallaUrlRaw !== this.pantallaUrlRaw) {
      this.pantallaUrlRaw = pantallaUrlRaw;
      this.pantallaUrl = this.sanitizer.bypassSecurityTrustResourceUrl(pantallaUrlRaw);
    }
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
