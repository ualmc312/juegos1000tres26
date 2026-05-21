import { CommonModule } from '@angular/common';
import { Component, Input, OnDestroy, OnInit, ChangeDetectorRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ReflejosP2PService } from './reflejos-p2p.service';
import { ReflejosEstado, ReflejosResultadoItem } from './comunicacion/reflejos.model';

@Component({
  selector: 'app-reflejos-p2p',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reflejos-p2p.component.html',
  styleUrl: './reflejos-p2p.component.css',
  providers: [ReflejosP2PService],
})
export class ReflejosP2PComponent implements OnInit, OnDestroy {
  @Input() uuid = '';
  @Input() jugadorId = '';
  @Input() esHost = false;
  @Input() hostId = '';
  @Input() hostPeerId = '';
  @Input() rol: 'host' | 'player' | 'pantalla' = 'player';

  estado: ReflejosEstado = {
    rondaId: '',
    fase: 'ESPERANDO',
    palabraActual: '',
    mensaje: 'Preparando reflejos',
    inicioLocalMs: 0,
    fuegoLocalMs: 0,
    duracionMs: 0,
    ranking: [] as ReflejosResultadoItem[],
    ganadorId: null,
    reacciones: [] as ReflejosResultadoItem[],
  };
  salaManual = '';
  peerIdActual = ''; // Mostrar el peerId actual del servicio
  errorInicial = ''; // Para mostrar errores de inicialización
  private redireccionProgramada = false;
  private backendNotificado = false;
  private esPopup = false;

  private sub?: Subscription;

  constructor(
    private readonly reflejos: ReflejosP2PService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.paramMap;
    const query = this.route.snapshot.queryParamMap;

    const sala = this.uuid || params.get('uuid') || this.salaManual || 'reflejos';
    const rolParam = query.get('role') as 'host' | 'player' | 'pantalla' | null;
    const rol = rolParam || this.rol || (this.esHost ? 'host' : 'player');
    const hostPeerId = this.hostPeerId || query.get('hostPeerId') || undefined;
    
    // Leer jugadorId de los query params y guardarlo en sessionStorage (vital para el host en nueva pestaña)
    const queryJugadorId = query.get('jugadorId');
    if (queryJugadorId) {
      this.jugadorId = queryJugadorId;
      sessionStorage.setItem('sala.jugadorId', this.jugadorId);
    } else if (!this.jugadorId) {
      this.jugadorId = sessionStorage.getItem('sala.jugadorId') || '';
    }

    this.esPopup = query.get('popup') === 'true';

    this.uuid = sala;
    this.rol = rol;
    this.esHost = rol === 'host';
    this.hostPeerId = hostPeerId || '';

    try {
      this.reflejos.inicializar(sala, rol, hostPeerId, this.jugadorId);
      
      // Obtener el peerId del servicio para mostrarlo
      this.peerIdActual = this.reflejos.getPeerId();
      
      this.sub = this.reflejos.getEstado$().subscribe((estado) => {
        this.estado = estado;
        this.cdr.detectChanges();

        if (estado.fase === 'RESULTADO' && !this.redireccionProgramada) {
          this.redireccionProgramada = true;
          
          if (this.esHost && !this.backendNotificado) {
            // El host avisa al backend inmediatamente para que la sala se actualice
            this.backendNotificado = true;
            const actorId = sessionStorage.getItem('sala.jugadorId') || '';
            this.reflejos.finalizarJuegoEnBackend(actorId);
          }

          // Ambos esperan 5 segundos para que los usuarios vean los resultados antes de salir
          setTimeout(() => {
            this.volverALobby();
          }, 5000);
        }
      });
    } catch (error) {
      const mensaje = error instanceof Error ? error.message : 'Error desconocido';
      this.errorInicial = mensaje;
      console.error('Error al inicializar reflejos P2P:', error);
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.reflejos.desconectar();
  }

  pulsarFuego(): void {
    this.reflejos.registrarReaccion();
  }

  volverALobby(): void {
    if (this.esHost && !this.backendNotificado) {
      this.backendNotificado = true;
      const actorId = sessionStorage.getItem('sala.jugadorId') || '';
      this.reflejos.finalizarJuegoEnBackend(actorId).finally(() => {
        this.ejecutarCierre();
      });
    } else {
      this.ejecutarCierre();
    }
  }

  private ejecutarCierre(): void {
    if (this.esPopup || window.opener) {
      // Si se abrió en una pestaña nueva (como suele hacer el host), la cerramos
      window.close();
      
      // Fallback por si el navegador bloquea el cierre
      setTimeout(() => this.router.navigate(['/sala', this.uuid]), 300);
    } else {
      // Si es un player que navegó en la misma pestaña
      this.router.navigate(['/sala', this.uuid]);
    }
  }

  get esBotonHabilitado(): boolean {
    return this.estado.fase === 'FUEGO' || this.estado.fase === 'PALABRA';
  }
}
