import { Evento } from '../../../../core/comunicacion';
import { ReflejosSecuenciaEnviable } from './reflejos-secuencia-enviable';
import { ReflejosSecuenciaPayload } from './reflejos.model';

export class ReflejosSecuenciaEvento implements Evento<string> {
  constructor(private readonly onSecuencia: (payload: ReflejosSecuenciaPayload) => void) {}

  hacer(payload: string): void {
    try {
      const enviable = new ReflejosSecuenciaEnviable({
        comando: 'SECUENCIA_REFLEJOS',
        rondaId: '',
        hostId: '',
        inicioLocalMs: 0,
        duracionMs: 0,
        pasos: [],
      });
      enviable.in(payload);
      this.onSecuencia(enviable.getPayload());
    } catch {
      // Ignorar payloads malformados.
    }
  }
}
