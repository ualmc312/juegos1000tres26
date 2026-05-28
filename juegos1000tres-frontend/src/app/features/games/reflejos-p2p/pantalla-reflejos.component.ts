import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ReflejosP2PService } from './reflejos-p2p.service';
import { ReflejosEstado } from './comunicacion/reflejos.model';
import { obtenerApiBaseUrl } from '../../../core/config/api-base';

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
  private readonly apiBase = obtenerApiBaseUrl();
  private readonly requestOptions = { withCredentials: true };
  private jugadoresPorId = new Map<string, string>();

  private sub?: Subscription;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly reflejos: ReflejosP2PService,
    private readonly http: HttpClient
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    const sala = this.uuid || params.get('uuid') || 'reflejos';

    this.uuid = sala;

    this.cargarNombresSala();

    try {
      // Inicializar como observador (no participa en el juego)
      this.reflejos.inicializar(sala, 'host', undefined);

      this.sub = this.reflejos.getEstado$().subscribe((estado: ReflejosEstado) => {
        this.estado = estado;
        if (estado.fase === 'RESULTADO' && estado.ranking.length && this.jugadoresPorId.size === 0) {
          this.cargarNombresSala();
        }
      });
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

  obtenerNombreJugador(id: string | null): string {
    if (!id) {
      return '';
    }
    return this.jugadoresPorId.get(id) || id;
  }

  private cargarNombresSala(): void {
    if (!this.uuid) {
      return;
    }

    this.http
      .get<SalaRespuesta>(`${this.apiBase}/sala/${this.uuid}/estado`, this.requestOptions)
      .subscribe({
        next: (respuesta: SalaRespuesta) => {
          const jugadores = respuesta.jugadores || [];
          const mapa = new Map(jugadores.map((jugador: JugadorResumen) => [jugador.id, jugador.nombre]));
          if (respuesta.p2pHostPeerId && respuesta.hostId) {
            const hostNombre = mapa.get(respuesta.hostId);
            if (hostNombre) {
              mapa.set(respuesta.p2pHostPeerId, hostNombre);
            }
          }
          this.jugadoresPorId = mapa;
        },
        error: () => {
          // Mantener ids si la sala no responde.
        }
      });
  }
}

interface JugadorResumen {
  id: string;
  nombre: string;
}

interface SalaRespuesta {
  uuid: string;
  jugadores: JugadorResumen[];
  hostId: string;
  p2pHostPeerId: string;
  pantallaId: string;
  juegoActual: string;
}
