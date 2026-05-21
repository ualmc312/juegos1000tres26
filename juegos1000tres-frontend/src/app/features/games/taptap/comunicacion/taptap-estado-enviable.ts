import { Enviable } from '../../../../core/comunicacion';

export interface TapTapEstadoData {
  inicioEpochMs?: number;
  duracionMs?: number;
  restanteMs?: number;
  finalizada?: boolean;
  ganadorId?: string | null;
  puntuaciones?: Array<{ jugadorId: string; nombre: string; puntos: number }>;
}

export class TapTapEstadoEnviable extends Enviable {
  private estado: TapTapEstadoData;

  constructor(estado: TapTapEstadoData = {}) {
    super();
    this.estado = estado;
  }

  out(): string {
    return JSON.stringify(this.estado);
  }

  in(entrada: unknown): void {
    if (typeof entrada === 'string' && entrada.trim()) {
      try {
        this.estado = JSON.parse(entrada) as TapTapEstadoData;
      } catch {
        // Si falla el parse, mantener estado vacío
        this.estado = {};
      }
    }
  }

  getEstado(): TapTapEstadoData {
    return this.estado;
  }
}
