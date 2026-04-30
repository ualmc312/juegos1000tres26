import { CommonModule } from '@angular/common';
import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Subscription, interval } from 'rxjs';
import { TapTapEstado, TapTapPuntuacion, TapTapService } from './services/taptap.service';

interface TapTarget {
  id: number;
  left: number;
  top: number;
  expiresAt: number;
}

@Component({
  selector: 'app-taptap',
  imports: [CommonModule],
  templateUrl: './taptap.html',
  styleUrl: './taptap.css',
})
export class Taptap implements OnInit, OnDestroy {
  @Input() uuid = '';
  @Input() jugadorId = '';
  @Input() pantallaId = '';
  @Input() esPantalla = false;
  @Input() esHost = false;

  puntos = 0;
  segundosRestantes = 60;
  estadoTexto = 'Preparando duelo...';
  objetivos: TapTarget[] = [];
  tabla: TapTapPuntuacion[] = [];

  private inicioEpochMs = 0;
  private duracionMs = 60_000;
  private finalizado = false;
  private finalizacionEnviada = false;
  private cargandoEstado = false;
  private contadorObjetivos = 1;
  private ultimoSegundoMostrado = -1;

  private readonly vidaObjetivoMs = 3_000;
  private readonly intervaloObjetivoMs = 700;

  private tickSub?: Subscription;
  private spawnSub?: Subscription;
  private estadoSub?: Subscription;

  constructor(private readonly tapTapService: TapTapService) {}

  ngOnInit(): void {
    if (!this.uuid) {
      return;
    }

    this.cargarEstado(true);
    this.estadoSub = interval(1000).subscribe(() => this.cargarEstado(false));
    this.tickSub = interval(250).subscribe(() => this.actualizarReloj());

    if (!this.esPantalla) {
      this.spawnSub = interval(this.intervaloObjetivoMs).subscribe(() => this.generarObjetivo());
    }
  }

  ngOnDestroy(): void {
    this.tickSub?.unsubscribe();
    this.spawnSub?.unsubscribe();
    this.estadoSub?.unsubscribe();
  }

  tocarObjetivo(objetivo: TapTarget): void {
    this.objetivos = this.objetivos.filter(item => item.id !== objetivo.id);

    if (this.finalizado || !this.uuid || !this.jugadorId) {
      return;
    }

    this.puntos += 1;

    this.tapTapService.registrarPunto(this.uuid, this.jugadorId).subscribe({
      next: respuesta => {
        this.puntos = respuesta.puntos;
      }
    });
  }

  private cargarEstado(forzar: boolean): void {
    if (!this.uuid || (this.cargandoEstado && !forzar)) {
      return;
    }

    this.cargandoEstado = true;

    this.tapTapService.obtenerEstado(this.uuid).subscribe({
      next: estado => {
        this.aplicarEstado(estado);
        this.cargandoEstado = false;
      },
      error: () => {
        this.cargandoEstado = false;
      }
    });
  }

  private aplicarEstado(estado: TapTapEstado): void {
    this.inicioEpochMs = estado.inicioEpochMs;
    this.duracionMs = estado.duracionMs;
    const pantallaId = this.pantallaId && this.pantallaId !== 'NINGUNO' ? this.pantallaId : '';
    const puntuaciones = pantallaId
      ? estado.puntuaciones.filter(item => item.jugadorId !== pantallaId)
      : estado.puntuaciones;
    this.tabla = [...puntuaciones].sort((a, b) => b.puntos - a.puntos);

    if (this.jugadorId) {
      const entry = estado.puntuaciones.find(item => item.jugadorId === this.jugadorId);
      if (entry) {
        this.puntos = entry.puntos;
      }
    }

    if (estado.finalizada) {
      this.finalizado = true;
      this.estadoTexto = 'Duelo terminado';
    }
  }

  private actualizarReloj(): void {
    if (!this.inicioEpochMs) {
      return;
    }

    const ahora = Date.now();
    const fin = this.inicioEpochMs + this.duracionMs;
    const restante = Math.max(0, fin - ahora);

    const segundos = Math.max(0, Math.ceil(restante / 1000));
    if (segundos !== this.ultimoSegundoMostrado) {
      this.segundosRestantes = segundos;
      this.ultimoSegundoMostrado = segundos;
    }
    this.estadoTexto = ahora < this.inicioEpochMs
      ? 'Preparando duelo...'
      : restante <= 0
        ? 'Duelo terminado'
        : 'A disparar';

    if (restante <= 0) {
      this.finalizado = true;
      this.objetivos = [];
      this.intentarFinalizar();
      return;
    }

    this.objetivos = this.objetivos.filter(objetivo => objetivo.expiresAt > ahora);
  }

  private generarObjetivo(): void {
    if (this.finalizado || this.esPantalla) {
      return;
    }

    const ahora = Date.now();
    if (ahora < this.inicioEpochMs || ahora > this.inicioEpochMs + this.duracionMs) {
      return;
    }

    if (this.objetivos.length >= 6) {
      return;
    }

    const left = 8 + Math.random() * 84;
    const top = 12 + Math.random() * 72;
    this.objetivos = [
      ...this.objetivos,
      {
        id: this.contadorObjetivos++,
        left,
        top,
        expiresAt: ahora + this.vidaObjetivoMs
      }
    ];
  }

  private intentarFinalizar(): void {
    if (this.finalizacionEnviada || !this.esHost || !this.uuid || !this.jugadorId) {
      return;
    }

    this.finalizacionEnviada = true;

    this.tapTapService.finalizarPartida(this.uuid, this.jugadorId).subscribe({
      next: () => {
        this.cargarEstado(true);
      },
      error: () => {
        this.finalizacionEnviada = false;
      }
    });
  }
}
