import { Enviable } from "../Enviable.js";
import { Envio } from "../Envio.js";

export class JsonEnvio extends Envio<string> {
  traducirEnviableAFormato(enviable: Enviable): string {
    if (!enviable) {
      throw new Error("El enviable es obligatorio");
    }

    if (typeof enviable.toJson !== "function") {
      throw new Error("El enviable debe implementar toJson");
    }

    return enviable.toJson();
  }

  getTipoPayload(): string {
    return "json-string";
  }
}
