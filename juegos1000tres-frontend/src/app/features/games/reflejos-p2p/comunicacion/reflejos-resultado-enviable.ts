import { Enviable } from '../../../../core/comunicacion';
import { ReflejosResultadoPayload } from './reflejos.model';

export class ReflejosResultadoEnviable extends Enviable {
  constructor(private payload: ReflejosResultadoPayload) {
    super();
  }

  out(): string {
    return JSON.stringify(this.payload);
  }

  in(entrada: unknown): void {
    if (typeof entrada !== 'string') {
      throw new Error('Se esperaba un string JSON para el resultado de reflejos');
    }

    this.payload = JSON.parse(entrada) as ReflejosResultadoPayload;
  }

  getPayload(): ReflejosResultadoPayload {
    return this.payload;
  }
}
