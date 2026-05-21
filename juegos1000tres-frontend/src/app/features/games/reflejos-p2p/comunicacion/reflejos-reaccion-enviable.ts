import { Enviable } from '../../../../core/comunicacion';
import { ReflejosReaccionPayload } from './reflejos.model';

export class ReflejosReaccionEnviable extends Enviable {
  constructor(private payload: ReflejosReaccionPayload) {
    super();
  }

  out(): string {
    return JSON.stringify(this.payload);
  }

  in(entrada: unknown): void {
    if (typeof entrada !== 'string') {
      throw new Error('Se esperaba un string JSON para la reaccion de reflejos');
    }

    this.payload = JSON.parse(entrada) as ReflejosReaccionPayload;
  }

  getPayload(): ReflejosReaccionPayload {
    return this.payload;
  }
}
