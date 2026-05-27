import { NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../auth/services/auth.service';
import { AuthSession } from '../auth/models/auth-session.model';
import { GenericButton } from '../../shared/components/generic-button/generic-button';
import { AmigosModal } from '../amigos/amigos-modal';

@Component({
  selector: 'app-home',
  imports: [GenericButton, NgIf, AmigosModal],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home implements OnInit {
  usuario: AuthSession | null = null;
  mostrarAmigos = false;
  esInvitado = false;

  constructor(private readonly auth: AuthService, private readonly router: Router) {}

  ngOnInit(): void {
    this.auth.loadSession().subscribe(user => {
      this.actualizarUsuario(user);
    });
    this.auth.currentUser$.subscribe(user => {
      this.actualizarUsuario(user);
    });
  }

  entrarInvitado(): void {
    this.auth.guest().subscribe({
      next: () => this.router.navigate(['/sala']),
      error: () => this.router.navigate(['/login'])
    });
  }

  cerrarSesion(): void {
    this.actualizarUsuario(null);
    this.auth.logout().subscribe({
      next: () => this.router.navigate(['/']),
      error: () => this.router.navigate(['/'])
    });
  }

  abrirAmigos(): void {
    if (this.esInvitado) {
      return;
    }
    this.mostrarAmigos = true;
  }

  irAHistorial(): void {
    if (this.esInvitado) {
      return;
    }

    this.router.navigate(['/historial']);
  }

  cerrarAmigos(): void {
    this.mostrarAmigos = false;
  }

  private actualizarUsuario(user: AuthSession | null): void {
    this.usuario = user;
    this.esInvitado = user?.role === 'GUEST';
  }
}
