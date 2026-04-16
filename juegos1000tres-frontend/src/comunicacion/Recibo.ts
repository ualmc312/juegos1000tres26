import { Enviable, type EnviableConstructor } from "./Enviable.js";

export abstract class Recibo<PAYLOAD> {
  abstract traducirFormatoAEnviable<T extends Enviable>(
    payload: PAYLOAD,
    tipoEnviable: EnviableConstructor<T>
  ): T;

  abstract getTipoPayload(): string;
}
