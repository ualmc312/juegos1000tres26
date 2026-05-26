import { CommonModule } from '@angular/common';
import { Component, EventEmitter, HostListener, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

@Component({
  selector: 'app-space-invaders',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './space-invaders.component.html',
  styleUrls: ['./space-invaders.component.css'],
})
export class SpaceInvadersComponent implements OnInit, OnChanges {
  @Input() uuid = '';
  @Input() jugadorId = '';
  @Input() nombreJugador = '';
  @Input() pantallaId = '';
  @Input() esPantalla = false;
  @Input() esHost = false;
  @Input() jugadores: SpaceInvadersJugador[] = [];
  @Output() volverSala = new EventEmitter<void>();

  estadoConexion = 'Preparando Space Invaders...';
  partidaFinalizada = false;
  gameUrl?: SafeResourceUrl;
  gameUrlRaw = '';
  private jugadoresFinalizados = new Set<string>();
  private jugadorIdPersistente = '';

  constructor(private readonly sanitizer: DomSanitizer) {}

  ngOnInit(): void {
    this.actualizarUrlJuego();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (
      changes['uuid']
      || changes['jugadorId']
      || changes['nombreJugador']
      || changes['esPantalla']
      || changes['pantallaId']
      || changes['esHost']
      || changes['jugadores']
    ) {
      if (changes['uuid']) {
        this.jugadoresFinalizados = new Set();
        this.partidaFinalizada = false;
      }

      this.actualizarUrlJuego();
    }
  }

  ngOnDestroy(): void {
    // Sin limpieza extra: el juego se destruye al desmontar el iframe.
  }

  private actualizarUrlJuego(): void {
    const salaId = this.uuid?.trim() || 'space-invaders';
    const backendSalaId = this.uuid?.trim() || 'space-invaders'; // debe ser el UUID de la sala
    const jugadorId = this.obtenerJugadorIdSeguro(salaId);
    const nombreJugador = this.nombreJugador?.trim() || 'Jugador';
    const jugadoresPartida = this.obtenerJugadoresPartida();

    const params = new URLSearchParams({
      salaId,
      backendSalaId,
      jugadorId,
      player: nombreJugador,
      esHost: String(this.esHost),
      jugadores: jugadoresPartida.join(','),
      backendUrl: 'http://localhost:8083',
    });

    const vista = this.esPantalla ? 'scoreboard.html' : 'index.html';
    const url = `/games/space-invaders/${vista}?${params.toString()}`;
    if (url === this.gameUrlRaw) {
      return;
    }

    this.gameUrlRaw = url;
    this.gameUrl = this.sanitizer.bypassSecurityTrustResourceUrl(url);
    this.estadoConexion = this.esPantalla
      ? `Sala ${salaId} - Pantalla asignada (${jugadorId})`
      : `Sala ${salaId} - Jugador ${jugadorId}`;
  }

  private obtenerJugadorIdSeguro(salaId: string): string {
    const explicit = this.jugadorId?.trim();
    if (explicit) {
      return explicit;
    }

    if (this.jugadorIdPersistente) {
      return this.jugadorIdPersistente;
    }

    const clave = `space-invaders-player-id:${salaId}`;
    const existente = this.leerLocalStorage(clave);
    if (existente) {
      this.jugadorIdPersistente = existente;
      return existente;
    }

    const nuevo = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `si-${Date.now()}-${Math.floor(Math.random() * 99999)}`;

    this.escribirLocalStorage(clave, nuevo);
    this.jugadorIdPersistente = nuevo;
    return nuevo;
  }

  private leerLocalStorage(clave: string): string {
    try {
      const valor = localStorage.getItem(clave);
      return valor && valor.trim() ? valor.trim() : '';
    } catch {
      return '';
    }
  }

  private escribirLocalStorage(clave: string, valor: string): void {
    try {
      localStorage.setItem(clave, valor);
    } catch {
      // Si el almacenamiento falla, seguimos con el id generado en memoria.
    }
  }

  private obtenerJugadoresPartida(): string[] {
    const pantallaAsignada = this.pantallaId && this.pantallaId !== 'NINGUNO' ? this.pantallaId : '';
    const ids = this.jugadores
      .map(jugador => jugador.id?.trim())
      .filter((id): id is string => !!id && id !== 'NINGUNO' && id !== pantallaAsignada);

    if (ids.length > 0) {
      return ids;
    }

    return this.jugadorId ? [this.jugadorId] : [];
  }

  @HostListener('window:message', ['$event'])
  onIframeMessage(event: MessageEvent): void {
    if (event.origin !== window.location.origin) {
      return;
    }

    const data = event.data as { type?: string; jugadorId?: string; puntuacionFinal?: number } | null;
    if (!data) {
      return;
    }

    if (data.type === 'SPACE_INVADERS_GAME_OVER') {
      this.marcarJugadorComoFinalizado(data.jugadorId);
      return;
    }

    if (data.type === 'SPACE_INVADERS_MATCH_END') {
      this.partidaFinalizada = true;
      this.estadoConexion = 'Partida finalizada. Ya puedes volver a la sala.';
      return;
    }

    if (data.type !== 'SPACE_INVADERS_RETURN_TO_LOBBY') {
      return;
    }

    if (data.jugadorId && this.jugadorId && data.jugadorId !== this.jugadorId) {
      return;
    }

    this.volverSala.emit();
  }

  volverALaSala(): void {
    this.volverSala.emit();
  }

  private marcarJugadorComoFinalizado(jugadorId?: string): void {
    if (!jugadorId) {
      return;
    }

    this.jugadoresFinalizados.add(jugadorId);

    const jugadoresActivos = this.obtenerJugadoresPartida();
    if (jugadoresActivos.length === 0) {
      return;
    }

    if (jugadoresActivos.every(id => this.jugadoresFinalizados.has(id))) {
      this.partidaFinalizada = true;
      this.estadoConexion = 'Partida finalizada. Ya puedes volver a la sala.';
    }
  }
}

interface SpaceInvadersJugador {
  id: string;
}
