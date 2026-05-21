import { Enviable } from '../../../../core/comunicacion';
import { ReflejosSecuenciaPayload } from './reflejos.model';

export class ReflejosSecuenciaEnviable extends Enviable {
  constructor(private payload: ReflejosSecuenciaPayload) {
    super();
  }

  out(): string {
    return JSON.stringify(this.payload);
  }

  in(entrada: unknown): void {
    if (typeof entrada !== 'string') {
      throw new Error('Se esperaba un string JSON para la secuencia de reflejos');
    }

    this.payload = JSON.parse(entrada) as ReflejosSecuenciaPayload;
  }

  getPayload(): ReflejosSecuenciaPayload {
    return this.payload;
  }
}
