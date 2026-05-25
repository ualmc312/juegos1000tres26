import { NgFor, NgIf, DatePipe } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { GenericButton } from '../../shared/components/generic-button/generic-button';
import { HistorialJuego, HistorialSala, HistorialService } from './historial.service';

@Component({
  selector: 'app-historial',
  imports: [NgIf, NgFor, DatePipe, GenericButton],
  templateUrl: './historial.html',
  styleUrl: './historial.css',
})
export class Historial implements OnInit {
  cargando = false;
  errorMensaje = '';
  salas: HistorialSala[] = [];

  constructor(
    private readonly historialService: HistorialService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.cargarHistorial();
  }

  cargarHistorial(): void {
    this.cargando = true;
    this.errorMensaje = '';
    this.cdr.detectChanges();

    this.historialService.obtenerHistorial().subscribe({
      next: salas => {
        this.salas = salas;
        this.cargando = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.salas = [];
        this.errorMensaje = 'No se pudo cargar el historial de salas';
        this.cargando = false;
        this.cdr.detectChanges();
      },
    });
  }

  trackSala(_: number, sala: HistorialSala): string {
    return sala.uuid;
  }

  trackJuego(_: number, juego: HistorialJuego): string {
    return `${juego.orden}-${juego.juegoNombre}-${juego.fechaJugado}`;
  }
}