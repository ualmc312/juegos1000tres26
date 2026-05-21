import { Evento } from '../../../../core/comunicacion';
import { ReflejosResultadoEnviable } from './reflejos-resultado-enviable';
import { ReflejosResultadoPayload } from './reflejos.model';

export class ReflejosResultadoEvento implements Evento<string> {
  constructor(private readonly onResultado: (payload: ReflejosResultadoPayload) => void) {}

  hacer(payload: string): void {
    try {
      const enviable = new ReflejosResultadoEnviable({
        comando: 'RESULTADO_REFLEJOS',
        rondaId: '',
        ganadorId: null,
        ranking: [],
      });
      enviable.in(payload);
      this.onResultado(enviable.getPayload());
    } catch {
      // Ignorar payloads malformados.
    }
  }
}
