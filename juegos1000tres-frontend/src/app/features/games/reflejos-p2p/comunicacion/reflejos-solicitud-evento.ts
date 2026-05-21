import { Evento } from '../../../../core/comunicacion';
import { ReflejosSolicitudSincronizacionEnviable } from './reflejos-solicitud-enviable';
import { ReflejosSolicitudSincronizacionPayload } from './reflejos-solicitud-enviable';

export class ReflejosSolicitudSincronizacionEvento implements Evento<string> {
  constructor(private readonly onSolicitud: (payload: ReflejosSolicitudSincronizacionPayload) => void) {}

  hacer(payload: string): void {
    try {
      const enviable = new ReflejosSolicitudSincronizacionEnviable({
        comando: 'SOLICITAR_SINCRONIZACION',
        jugadorId: '',
      });
      enviable.in(payload);
      this.onSolicitud(enviable.getPayload());
    } catch {
      // Ignorar payloads malformados.
    }
  }
}
