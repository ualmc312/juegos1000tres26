export const REFLEJOS_COMANDO_SECUENCIA = 'SECUENCIA_REFLEJOS';
export const REFLEJOS_COMANDO_REACCION = 'REGISTRAR_REACCION';
export const REFLEJOS_COMANDO_RESULTADO = 'RESULTADO_REFLEJOS';
export const REFLEJOS_COMANDO_SOLICITAR_SINCRONIZACION = 'SOLICITAR_SINCRONIZACION';

export type ReflejosPasoTipo = 'CUENTA_ATRAS' | 'PALABRA' | 'FUEGO';

export interface ReflejosPaso {
  tipo: ReflejosPasoTipo;
  texto: string;
  retrasoMs: number;
}

export interface ReflejosSecuenciaPayload {
  comando: typeof REFLEJOS_COMANDO_SECUENCIA;
  rondaId: string;
  hostId: string;
  inicioLocalMs: number;
  duracionMs: number;
  pasos: ReflejosPaso[];
}

export interface ReflejosReaccionPayload {
  comando: typeof REFLEJOS_COMANDO_REACCION;
  rondaId: string;
  jugadorId: string;
  tiempoMs: number;
  destinoId?: string;
}

export interface ReflejosResultadoItem {
  jugadorId: string;
  tiempoMs: number;
}

export interface ReflejosResultadoPayload {
  comando: typeof REFLEJOS_COMANDO_RESULTADO;
  rondaId: string;
  ganadorId: string | null;
  ranking: ReflejosResultadoItem[];
}

export interface ReflejosEstado {
  rondaId: string;
  fase: 'ESPERANDO' | 'CUENTA_ATRAS' | 'PALABRA' | 'FUEGO' | 'RESULTADO';
  palabraActual: string;
  mensaje: string;
  inicioLocalMs: number;
  fuegoLocalMs: number;
  duracionMs: number;
  ranking: ReflejosResultadoItem[];
  ganadorId: string | null;
  reacciones: ReflejosResultadoItem[];
}
