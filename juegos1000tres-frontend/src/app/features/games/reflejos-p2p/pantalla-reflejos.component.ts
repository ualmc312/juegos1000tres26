import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ReflejosP2PService } from './reflejos-p2p.service';
import { ReflejosEstado } from './comunicacion/reflejos.model';

@Component({
  selector: 'app-pantalla-reflejos',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './pantalla-reflejos.component.html',
  styleUrl: './pantalla-reflejos.component.css',
})
export class PantallaReflejosComponent implements OnInit, OnDestroy {
  uuid = '';
  estado: ReflejosEstado = {
    rondaId: '',
    fase: 'ESPERANDO',
    palabraActual: '',
    mensaje: 'Cargando...',
    inicioLocalMs: 0,
    fuegoLocalMs: 0,
    duracionMs: 0,
    ranking: [],
    ganadorId: null,
    reacciones: [],
  };
  errorInicial = '';

  private sub?: Subscription;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly reflejos: ReflejosP2PService
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    const sala = this.uuid || params.get('uuid') || 'reflejos';

    this.uuid = sala;

    try {
      // Inicializar como observador (no participa en el juego)
      this.reflejos.inicializar(sala, 'host', undefined);

      this.sub = this.reflejos.getEstado$().subscribe((estado) => (this.estado = estado));
    } catch (error) {
      const mensaje = error instanceof Error ? error.message : 'Error desconocido';
      this.errorInicial = mensaje;
      console.error('Error al inicializar pantalla reflejos:', error);
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.reflejos.desconectar();
  }

  volverALobby(): void {
    this.router.navigate(['/sala', this.uuid]);
  }
}
