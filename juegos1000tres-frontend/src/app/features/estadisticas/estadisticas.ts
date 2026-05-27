import { NgFor, NgIf } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, finalize, map, of, switchMap } from 'rxjs';
import { GenericButton } from '../../shared/components/generic-button/generic-button';
import { AuthService } from '../auth/services/auth.service';
import { HistorialSala, HistorialService } from '../historial/historial.service';

interface JuegoEstadistica {
  juegoNombre: string;
  victorias: number;
  partidas: number;
  altura: number;
}

@Component({
  selector: 'app-estadisticas',
  standalone: true,
  imports: [NgIf, NgFor, GenericButton],
  templateUrl: './estadisticas.html',
  styleUrl: './estadisticas.css',
})
export class Estadisticas implements OnInit {
  cargando = false;
  errorMensaje = '';
  usuarioNombre = '';
  estadisticas: JuegoEstadistica[] = [];
  maxVictorias = 0;
  ticks: number[] = [0];
  totalVictorias = 0;

  constructor(
    private readonly authService: AuthService,
    private readonly historialService: HistorialService,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.cargarEstadisticas();
  }

  cargarEstadisticas(): void {
    this.cargando = true;
    this.errorMensaje = '';
    this.cdr.detectChanges();

    this.authService.loadSession().pipe(
      switchMap(usuario => {
        if (!usuario || usuario.role !== 'USER') {
          throw new Error('No se encontró un usuario registrado');
        }

        this.usuarioNombre = usuario.nombre;
        return this.historialService.obtenerHistorial();
      }),
      map(salas => this.calcularEstadisticas(salas)),
      catchError(() => {
        this.estadisticas = [];
        this.errorMensaje = 'No se pudieron cargar las estadísticas';
        return of([] as JuegoEstadistica[]);
      }),
      finalize(() => {
        this.cargando = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: estadisticas => {
        this.estadisticas = estadisticas;
        this.maxVictorias = estadisticas[0]?.victorias ?? 0;
        this.ticks = this.crearTicks(this.maxVictorias);
        this.totalVictorias = estadisticas.reduce((acumulado, item) => acumulado + item.victorias, 0);
        this.cdr.detectChanges();
      },
    });
  }

  volverAlInicio(): void {
    this.router.navigate(['/']);
  }

  trackJuego(_: number, juego: JuegoEstadistica): string {
    return juego.juegoNombre;
  }

  private calcularEstadisticas(salas: HistorialSala[]): JuegoEstadistica[] {
    const nombreUsuario = this.usuarioNombre.trim().toLowerCase();
    const acumulado = new Map<string, { victorias: number; partidas: number }>();

    for (const sala of salas) {
      const juegos = 'juegosJugados' in sala ? sala.juegosJugados : [];

      for (const juego of juegos) {
        const ganadasPorUsuario = juego.ganadores.some(nombre => nombre.trim().toLowerCase() === nombreUsuario);
        const actual = acumulado.get(juego.juegoNombre) ?? { victorias: 0, partidas: 0 };

        actual.partidas += 1;
        if (ganadasPorUsuario) {
          actual.victorias += 1;
        }

        acumulado.set(juego.juegoNombre, actual);
      }
    }

    const maximo = Math.max(...Array.from(acumulado.values()).map(valor => valor.victorias), 0);

    return Array.from(acumulado.entries())
      .map(([juegoNombre, valor]) => ({
        juegoNombre,
        victorias: valor.victorias,
        partidas: valor.partidas,
        altura: maximo > 0 ? (valor.victorias / maximo) * 100 : 0,
      }))
      .sort((izquierda, derecha) => derecha.victorias - izquierda.victorias || izquierda.juegoNombre.localeCompare(derecha.juegoNombre, 'es', { sensitivity: 'base' }));
  }

  private crearTicks(maximo: number): number[] {
    if (maximo <= 0) {
      return [0];
    }

    const pasos = Math.min(5, maximo);
    const ticks = new Set<number>();

    for (let indice = 0; indice <= pasos; indice += 1) {
      ticks.add(Math.round((maximo * indice) / pasos));
    }

    return Array.from(ticks).sort((izquierda, derecha) => izquierda - derecha);
  }
}