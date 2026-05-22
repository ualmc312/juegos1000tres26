import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, catchError, of, tap } from 'rxjs';
import { AuthSession } from '../models/auth-session.model';
import { Usuario } from '../models/usuario.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = 'http://localhost:8083/api/usuarios';
  private readonly AUTH_URL = 'http://localhost:8083/auth';
  private readonly currentUserSubject = new BehaviorSubject<AuthSession | null>(null);
  readonly currentUser$ = this.currentUserSubject.asObservable();
  private readonly storageKey = 'auth.session';

  constructor(private readonly http: HttpClient) {
    const stored = sessionStorage.getItem(this.storageKey);
    if (stored) {
      try {
        const parsed = JSON.parse(stored) as AuthSession;
        if (parsed.role === 'USER' && !parsed.id) {
          this.clearSession();
        } else {
          this.currentUserSubject.next(parsed);
        }
      } catch {
        sessionStorage.removeItem(this.storageKey);
      }
    }
  }

  login(email: string, password: string): Observable<AuthSession> {
    return this.http
      .post<AuthSession>(
        `${this.AUTH_URL}/login`,
        { email, password },
        { withCredentials: true }
      )
      .pipe(tap(user => this.saveSession(user)));
  }

  guest(): Observable<AuthSession> {
    return this.http
        .post<AuthSession>(`${this.AUTH_URL}/guest`, null, { withCredentials: true })
        .pipe(tap(user => this.saveSession(user)));
  }

  logout(): Observable<void> {
    return this.http
        .post<void>(`${this.AUTH_URL}/logout`, null, { withCredentials: true })
        .pipe(tap(() => this.clearSession()));
  }

  loadSession(): Observable<AuthSession | null> {
    const current = this.currentUserSubject.value;
    if (current && (current.role === 'GUEST' || current.id != null)) {
      return of(this.currentUserSubject.value);
    }

    return this.http.get<AuthSession>(`${this.AUTH_URL}/me`, { withCredentials: true }).pipe(
      tap(user => this.saveSession(user)),
      catchError(() => {
        this.clearSession();
        return of(null);
      })
    );
  }

  private saveSession(user: AuthSession): void {
    this.currentUserSubject.next(user);
    sessionStorage.setItem(this.storageKey, JSON.stringify(user));
  }

  private clearSession(): void {
    this.currentUserSubject.next(null);
    sessionStorage.removeItem(this.storageKey);
  }

  register(usuario: Usuario): Observable<Usuario> {
    return this.http.post<Usuario>(this.API_URL, usuario);
  }

  existeEmail(email: string): Observable<boolean> {
    return this.http.get<boolean>(`${this.API_URL}/search/existe-email`, {
      params: new HttpParams().set('email', email)
    });
  }

  registroDisponible(email: string, nombre: string): Observable<boolean> {
    const params = new HttpParams().set('email', email).set('nombre', nombre);
    return this.http.get<boolean>(`${this.API_URL}/search/registro-disponible`, { params });
  }
}
