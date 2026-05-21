import { Enviable } from '../../../../core/comunicacion';

export interface ReflejosSolicitudSincronizacionPayload {
  comando: 'SOLICITAR_SINCRONIZACION';
  rondaId?: string;
  jugadorId: string;
  destinoId?: string;
}

export class ReflejosSolicitudSincronizacionEnviable extends Enviable {
  constructor(private payload: ReflejosSolicitudSincronizacionPayload) {
    super();
  }

  out(): string {
    return JSON.stringify(this.payload);
  }

  in(entrada: unknown): void {
    if (typeof entrada !== 'string') {
      throw new Error('Se esperaba un string JSON para la solicitud de sincronizacion');
    }

    this.payload = JSON.parse(entrada) as ReflejosSolicitudSincronizacionPayload;
  }

  getPayload(): ReflejosSolicitudSincronizacionPayload {
    return this.payload;
  }
}
