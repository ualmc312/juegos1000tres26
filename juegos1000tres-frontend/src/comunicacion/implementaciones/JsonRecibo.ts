import { Enviable, type EnviableConstructor } from "../Enviable.js";
import { Recibo } from "../Recibo.js";

export class JsonRecibo extends Recibo<string> {
  traducirFormatoAEnviable<T extends Enviable>(
    payload: string,
    tipoEnviable: EnviableConstructor<T>
  ): T {
    if (typeof payload !== "string") {
      throw new Error("El payload JSON debe ser un string");
    }

    if (typeof tipoEnviable !== "function") {
      throw new Error("Debes indicar una clase de mensaje entrante");
    }

    const instancia = new tipoEnviable();

    if (typeof instancia.fromJson !== "function") {
      throw new Error("La clase de mensaje debe implementar fromJson");
    }

    instancia.fromJson(payload);
    return instancia;
  }

  getTipoPayload(): string {
    return "json-string";
  }
}
