import { ContextoEvento } from "./ContextoEvento.js";
import { Evento } from "./Evento.js";

export abstract class Recibo<PAYLOAD> {
  abstract conEvento(comando: string, evento: Evento<PAYLOAD>): Recibo<PAYLOAD>;

  abstract procesar(payload: PAYLOAD, contexto: ContextoEvento): Promise<void> | void;

  abstract getTipoPayload(): string;
}
