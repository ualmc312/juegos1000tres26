import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
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

  estadoConexion = 'Preparando Space Invaders...';
  gameUrl?: SafeResourceUrl;
  gameUrlRaw = '';

  constructor(private readonly sanitizer: DomSanitizer) {}

  ngOnInit(): void {
    this.actualizarUrlJuego();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['uuid'] || changes['jugadorId'] || changes['nombreJugador'] || changes['esPantalla'] || changes['pantallaId']) {
      this.actualizarUrlJuego();
    }
  }

  ngOnDestroy(): void {
    // Sin limpieza extra: el juego se destruye al desmontar el iframe.
  }

  private actualizarUrlJuego(): void {
    const salaId = this.uuid?.trim() || 'space-invaders';
    const backendSalaId = this.uuid?.trim() || 'space-invaders'; // debe ser el UUID de la sala
    const jugadorId = this.jugadorId?.trim() || 'jugador-space-invaders';
    const nombreJugador = this.nombreJugador?.trim() || 'Jugador';

    const params = new URLSearchParams({
      salaId,
      backendSalaId,
      jugadorId,
      player: nombreJugador,
      backendUrl: 'http://localhost:8083',
    });

    const vista = this.esPantalla ? 'scoreboard.html' : 'index.html';
    const url = `/games/space-invaders/${vista}?${params.toString()}`;
    this.gameUrlRaw = url;
    this.gameUrl = this.sanitizer.bypassSecurityTrustResourceUrl(url);
    this.estadoConexion = this.esPantalla
      ? `Sala ${salaId} - Pantalla asignada (${jugadorId})`
      : `Sala ${salaId} - Jugador ${jugadorId}`;
  }
}
