import { Enviable } from "./Enviable.js";

export abstract class Envio<PAYLOAD> {
  abstract traducirEnviableAFormato(enviable: Enviable): PAYLOAD;

  abstract getTipoPayload(): string;
}
