import { Evento } from '../../../../core/comunicacion';
import { ReflejosReaccionEnviable } from './reflejos-reaccion-enviable';
import { ReflejosReaccionPayload } from './reflejos.model';

export class ReflejosReaccionEvento implements Evento<string> {
  constructor(private readonly onReaccion: (payload: ReflejosReaccionPayload) => void) {}

  hacer(payload: string): void {
    try {
      const enviable = new ReflejosReaccionEnviable({
        comando: 'REGISTRAR_REACCION',
        rondaId: '',
        jugadorId: '',
        tiempoMs: 0,
      });
      enviable.in(payload);
      this.onReaccion(enviable.getPayload());
    } catch {
      // Ignorar payloads malformados.
    }
  }
}
