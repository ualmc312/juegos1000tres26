import { Evento } from '../../../../core/comunicacion';
import { TapTapEstadoEnviable } from './taptap-estado-enviable';

export class TapTapEstadoEvento implements Evento<string> {
  constructor(
    private readonly onEstado: (estado: TapTapEstadoEnviable) => void
  ) {}

  hacer(payload: string): void {
    try {
      const enviable = new TapTapEstadoEnviable();
      enviable.in(payload);
      this.onEstado(enviable);
    } catch {
      // Ignorar payloads no válidos
    }
  }
}
