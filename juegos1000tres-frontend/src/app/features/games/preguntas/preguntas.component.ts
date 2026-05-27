import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { PreguntasService } from './preguntas.service';
import { EstadoPreguntas } from './modelos';

@Component({
  selector: 'app-preguntas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './preguntas.component.html',
  styleUrls: ['./preguntas.component.css'],
  providers: [PreguntasService],
})
export class PreguntasComponent implements OnInit, OnDestroy {
  @Input() uuid = '';
  @Input() jugadorId = '';
  @Input() pantallaId = '';
  @Input() esPantalla = false;
  @Input() esHost = false;
  @Output() volverSala = new EventEmitter<void>();

  estado: Partial<EstadoPreguntas> = {};
  respuestaLocal = '';

  private destroy$ = new Subject<void>();

  constructor(private readonly preguntasService: PreguntasService) {}

  ngOnInit(): void {
    if (!this.uuid || !this.jugadorId) {
      return;
    }

    this.preguntasService.inicializar(this.uuid);
    this.preguntasService.registrarJugador(this.jugadorId, 'Jugador');

    this.preguntasService.getEstado$()
      .pipe(takeUntil(this.destroy$))
      .subscribe((estado) => {
        this.estado = estado;
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.preguntasService.desconectar();
  }

  iniciarRonda(): void {
    this.preguntasService.iniciarRonda(this.jugadorId);
  }

  alActualizarRespuesta(texto: string): void {
    this.respuestaLocal = texto;
    this.preguntasService.actualizarBorrador(texto, this.jugadorId);
  }

  enviarRespuesta(): void {
    const texto = this.respuestaLocal.trim();
    if (!texto) {
      return;
    }

    this.preguntasService.enviarRespuesta(this.jugadorId, texto);
    this.respuestaLocal = '';
  }

  elegirRespuesta(opcionId: string): void {
    this.preguntasService.elegirRespuesta(this.jugadorId, opcionId);
  }

  getNombreFase(): string {
    switch (this.estado.fase) {
      case 'ESPERANDO_JUGADORES': return 'Esperando jugadores';
      case 'RESPONDIENDO': return 'Respondiendo';
      case 'ELEGIENDO': return 'Eligiendo respuesta';
      case 'MOSTRANDO_RESULTADO': return 'Mostrando resultado';
      default: return 'Sin estado';
    }
  }

  esSoyElegido(): boolean {
    return this.estado.jugadorElegido?.jugadorId === this.jugadorId;
  }

  yaRespondi(): boolean {
    return !!this.estado.respondedoresPendientes && !this.estado.respondedoresPendientes.includes(this.jugadorId);
  }

  getTiempoRestanteSegundos(): number {
    return Math.ceil((this.estado.tiempoRestanteMs || 0) / 1000);
  }

  get puedeVolverALaSala(): boolean {
    return this.estado.fase === 'MOSTRANDO_RESULTADO';
  }

  volverALaSala(): void {
    if (!this.esHost || !this.puedeVolverALaSala) {
      return;
    }

    this.preguntasService.finalizarPartida(this.jugadorId);
    this.volverSala.emit();
  }
}
