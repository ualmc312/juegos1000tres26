import { Component, OnInit, Input, Output, EventEmitter, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AmigosService, Usuario, SolicitudAmistad } from './amigos.service';
import { AuthSession } from '../auth/models/auth-session.model';
import { catchError, finalize, forkJoin, of } from 'rxjs';

@Component({
  selector: 'app-amigos-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './amigos-modal.html',
  styleUrl: './amigos-modal.css'
})
export class AmigosModal implements OnInit {
  @Input() usuarioActual: AuthSession | null = null;
  @Output() cerrar = new EventEmitter<void>();

  tab: 'amigos' | 'solicitudes' = 'amigos';
  amigos: Usuario[] = [];
  solicitudesRecibidas: SolicitudAmistad[] = [];
  solicitudesEnviadas: SolicitudAmistad[] = [];
  
  cargando = false;
  operacionEnCurso = false;
  private cargaInicialTimeoutId: ReturnType<typeof setTimeout> | null = null;
  mostrarAnadirAmigo = false;
  busquedaUsuario = '';
  usuarioSeleccionado: Usuario | null = null;
  errorMensaje = '';
  exitoMensaje = '';
  usuariosBusqueda: Usuario[] = [];
  mostrandoResultadosBusqueda = false;

  constructor(
    private readonly amigosService: AmigosService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.cargarDatos();
  }

  cargarDatos(): void {
    if (!this.usuarioActual?.id) return;

    if (this.cargaInicialTimeoutId) {
      clearTimeout(this.cargaInicialTimeoutId);
    }

    this.cargando = true;
    this.cargaInicialTimeoutId = setTimeout(() => {
      if (this.cargando) {
        this.cargando = false;
        this.cdr.detectChanges();
      }
    }, 2000);

    this.amigosService.obtenerAmigos(this.usuarioActual.id).pipe(
      catchError((err) => {
        console.error('Error al cargar amigos:', err);
        this.mostrarError('Error al cargar la lista de amigos');
        return of([] as Usuario[]);
      })
    ).pipe(
      finalize(() => {
        if (this.cargaInicialTimeoutId) {
          clearTimeout(this.cargaInicialTimeoutId);
          this.cargaInicialTimeoutId = null;
        }
        this.cargando = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (amigos) => {
        this.amigos = amigos;
        this.cdr.detectChanges();
        this.cargarSolicitudesSinBloqueo();
      }
    });
  }

  private cargarSolicitudesSinBloqueo(): void {
    if (!this.usuarioActual?.id) return;

    const solicitudesRecibidas$ = this.amigosService.obtenerSolicitudesRecibidas(this.usuarioActual.id).pipe(
      catchError((err) => {
        console.error('Error al cargar solicitudes recibidas:', err);
        return of([] as SolicitudAmistad[]);
      })
    );

    const solicitudesEnviadas$ = this.amigosService.obtenerSolicitudesEnviadas(this.usuarioActual.id).pipe(
      catchError((err) => {
        console.error('Error al cargar solicitudes enviadas:', err);
        return of([] as SolicitudAmistad[]);
      })
    );

    forkJoin({
      solicitudesRecibidas: solicitudesRecibidas$,
      solicitudesEnviadas: solicitudesEnviadas$
    }).subscribe({
      next: ({ solicitudesRecibidas, solicitudesEnviadas }) => {
        this.solicitudesRecibidas = solicitudesRecibidas;
        this.solicitudesEnviadas = solicitudesEnviadas;
        this.cdr.detectChanges();
      }
    });
  }

  eliminarAmigo(amigoId: number): void {
    if (!this.usuarioActual?.id) return;
    if (this.operacionEnCurso) return;

    if (!confirm('¿Estás seguro de que quieres eliminar este amigo?')) {
      return;
    }

    this.operacionEnCurso = true;

    this.amigosService.eliminarAmistad(this.usuarioActual.id, amigoId).subscribe({
      next: () => {
        this.amigos = this.amigos.filter(a => a.id !== amigoId);
        this.mostrarExito('Amigo eliminado correctamente');
        this.cargarSolicitudesSinBloqueo();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error al eliminar amigo:', err);
        this.mostrarError('Error al eliminar amigo');
      },
      complete: () => {
        this.operacionEnCurso = false;
        this.cdr.detectChanges();
      }
    });
  }

  aceptarSolicitud(solicitud: SolicitudAmistad): void {
    if (this.operacionEnCurso) return;
    this.operacionEnCurso = true;

    this.amigosService.aceptarSolicitud(solicitud.id).subscribe({
      next: () => {
        this.solicitudesRecibidas = this.solicitudesRecibidas.filter(s => s.id !== solicitud.id);
        this.amigos.push(solicitud.usuarioSolicitante);
        this.mostrarExito('Solicitud aceptada');
        this.cargarSolicitudesSinBloqueo();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error al aceptar solicitud:', err);
        this.mostrarError('Error al aceptar solicitud');
      },
      complete: () => {
        this.operacionEnCurso = false;
        this.cdr.detectChanges();
      }
    });
  }

  rechazarSolicitud(solicitud: SolicitudAmistad): void {
    if (this.operacionEnCurso) return;
    this.operacionEnCurso = true;

    this.amigosService.rechazarSolicitud(solicitud.id).subscribe({
      next: () => {
        this.solicitudesRecibidas = this.solicitudesRecibidas.filter(s => s.id !== solicitud.id);
        this.mostrarExito('Solicitud rechazada');
        this.cargarSolicitudesSinBloqueo();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error al rechazar solicitud:', err);
        this.mostrarError('Error al rechazar solicitud');
      },
      complete: () => {
        this.operacionEnCurso = false;
        this.cdr.detectChanges();
      }
    });
  }

  mostrarFormularioAnadir(): void {
    this.mostrarAnadirAmigo = true;
    this.busquedaUsuario = '';
    this.usuarioSeleccionado = null;
    this.usuariosBusqueda = [];
    this.mostrandoResultadosBusqueda = false;
    this.errorMensaje = '';
  }

  cancelarAnadir(): void {
    this.mostrarAnadirAmigo = false;
    this.busquedaUsuario = '';
    this.usuarioSeleccionado = null;
    this.usuariosBusqueda = [];
    this.mostrandoResultadosBusqueda = false;
    this.errorMensaje = '';
  }

  buscarUsuario(): void {
    const emailBuscado = this.busquedaUsuario.trim().toLowerCase();
    
    if (emailBuscado.length === 0) {
      this.usuarioSeleccionado = null;
      this.usuariosBusqueda = [];
      this.mostrandoResultadosBusqueda = false;
      this.errorMensaje = '';
      return;
    }

    // Evitar búsquedas demasiado frecuentes - esperar a que el usuario termine de escribir
    if (this.busquedaUsuario.trim().length < 3) {
      this.usuarioSeleccionado = null;
      this.usuariosBusqueda = [];
      this.mostrandoResultadosBusqueda = false;
      return;
    }

    const terminoBusqueda = this.busquedaUsuario.trim();

    // Buscar por email o nombre en el backend
    this.amigosService.buscarPorEmail(emailBuscado).subscribe({
      next: (usuarios) => {
        const usuariosFiltrados = usuarios.filter(usuario => usuario.id !== this.usuarioActual?.id);
        this.usuariosBusqueda = usuariosFiltrados;
        this.mostrandoResultadosBusqueda = usuariosFiltrados.length > 1;
        this.usuarioSeleccionado = usuariosFiltrados.length === 1 ? usuariosFiltrados[0] : null;
        this.errorMensaje = usuariosFiltrados.length === 0
          ? `No se encontraron usuarios para "${terminoBusqueda}"`
          : '';
      },
      error: (err) => {
        console.error('Error al buscar usuario:', err);
        this.usuarioSeleccionado = null;
        this.usuariosBusqueda = [];
        this.mostrandoResultadosBusqueda = false;
        if (err.status === 404) {
          this.mostrarError(`Usuario con email "${this.busquedaUsuario.trim()}" no encontrado`);
        } else if (err.status === 400) {
          this.mostrarError('Email inválido');
        } else {
          this.mostrarError('Error al buscar usuario');
        }
      }
    });
  }

  seleccionarUsuario(usuario: Usuario): void {
    this.usuarioSeleccionado = usuario;
    this.usuariosBusqueda = [usuario];
    this.mostrandoResultadosBusqueda = false;
    this.busquedaUsuario = usuario.email;
  }

  enviarSolicitud(): void {
    if (!this.usuarioActual?.id || !this.usuarioSeleccionado?.id) {
      this.mostrarError('Error: usuario no válido');
      return;
    }

    if (this.operacionEnCurso) return;
    this.operacionEnCurso = true;

    this.amigosService.enviarSolicitud(this.usuarioActual.id, this.usuarioSeleccionado.id).subscribe({
      next: () => {
        this.mostrarExito('Solicitud de amistad enviada');
        this.cancelarAnadir();
        this.cargarSolicitudesSinBloqueo();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error al enviar solicitud:', err);
        const errorMsg = err.error?.error || 'Error al enviar solicitud';
        this.mostrarError(errorMsg);
      },
      complete: () => {
        this.operacionEnCurso = false;
        this.cdr.detectChanges();
      }
    });
  }

  private mostrarError(mensaje: string): void {
    this.errorMensaje = mensaje;
    setTimeout(() => {
      this.errorMensaje = '';
    }, 3000);
  }

  private mostrarExito(mensaje: string): void {
    this.exitoMensaje = mensaje;
    setTimeout(() => {
      this.exitoMensaje = '';
    }, 3000);
  }
}
