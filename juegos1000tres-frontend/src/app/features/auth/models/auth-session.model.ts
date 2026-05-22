export interface AuthSession {
  id?: number;
  nombre: string;
  email: string;
  role: 'USER' | 'GUEST';
}
