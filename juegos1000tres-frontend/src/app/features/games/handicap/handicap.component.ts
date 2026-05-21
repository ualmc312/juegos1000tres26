import { CommonModule } from '@angular/common';
import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription, interval } from 'rxjs';
import { HandicapEstado, HandicapService } from './handicap.service';

@Component({
  selector: 'app-handicap',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './handicap.component.html',
  styleUrls: ['./handicap.component.css'],
})
export class HandicapComponent implements OnInit, OnDestroy {
  @Input() uuid = '';
  @Input() jugadorId = '';
  @Input() esHost = false;

  estado: HandicapEstado | null = null;
  seleccionados = new Set<string>();
  bloqueoHost = false;

  debugCookies = '';
  debugLocalStorage = '';
  debugSessionStorage = '';
  debugLocation = '';
  debugUserAgent = '';

  private estadoSub?: Subscription;

  constructor(private readonly handicapService: HandicapService) {}

  ngOnInit(): void {
    if (!this.uuid) {
      return;
    }

    this.cargarEstado();
    this.estadoSub = interval(1000).subscribe(() => this.cargarEstado());

    if (!this.esHost) {
      this.cargarDebugInfo();
    }
  }

  ngOnDestroy(): void {
    this.estadoSub?.unsubscribe();
  }

  toggleJugador(jugadorId: string, event: Event): void {
    if (this.bloqueoHost) {
      return;
    }

    const input = event.target as HTMLInputElement | null;
    if (!input) {
      return;
    }

    if (input.checked) {
      this.seleccionados.add(jugadorId);
    } else {
      this.seleccionados.delete(jugadorId);
    }
  }

  confirmarGanadores(): void {
    if (!this.esHost || this.bloqueoHost || !this.uuid || !this.jugadorId) {
      return;
    }

    const ganadores = Array.from(this.seleccionados.values());

    this.handicapService.confirmarGanadores(this.uuid, this.jugadorId, ganadores).subscribe({
      next: (estado) => this.aplicarEstado(estado),
    });
  }

  getTiempoRestanteSegundos(): number {
    if (!this.estado) {
      return 0;
    }
    return Math.ceil((this.estado.tiempoRestanteMs || 0) / 1000);
  }

  private cargarEstado(): void {
    if (!this.uuid) {
      return;
    }

    this.handicapService.obtenerEstado(this.uuid).subscribe({
      next: (estado) => this.aplicarEstado(estado),
      error: () => {
        // El juego puede finalizar mientras el componente sigue montado.
      }
    });
  }

  private aplicarEstado(estado: HandicapEstado): void {
    this.estado = estado;
    this.bloqueoHost = estado.hostBloqueado;

    if (estado.fase !== 'SELECCIONANDO') {
      this.seleccionados = new Set(estado.ganadores.map(item => item.id));
    }
  }

  private cargarDebugInfo(): void {
    if (typeof window === 'undefined' || typeof document === 'undefined') {
      return;
    }

    this.debugCookies = document.cookie || 'sin cookies';
    this.debugLocation = window.location.href;
    this.debugUserAgent = window.navigator.userAgent;
    this.debugLocalStorage = this.serializarStorage(window.localStorage);
    this.debugSessionStorage = this.serializarStorage(window.sessionStorage);
  }

  private serializarStorage(storage: Storage): string {
    const data: Record<string, string> = {};

    for (let i = 0; i < storage.length; i += 1) {
      const key = storage.key(i);
      if (!key) {
        continue;
      }
      data[key] = storage.getItem(key) ?? '';
    }

    const keys = Object.keys(data);
    if (keys.length === 0) {
      return 'sin datos';
    }

    return JSON.stringify(data, null, 2);
  }
}
