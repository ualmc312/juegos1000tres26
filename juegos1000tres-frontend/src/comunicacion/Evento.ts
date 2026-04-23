import { ContextoEvento } from "./ContextoEvento.js";

export interface Evento<PAYLOAD> {
  hacer(payload: PAYLOAD, contexto: ContextoEvento): void | Promise<void>;
}
