import { Enviable } from "../Enviable.js";
import { Envio } from "../Envio.js";

export class JsonEnvio extends Envio<string> {
  traducirEnviableAFormato(enviable: Enviable): string {
    if (!enviable) {
      throw new Error("El enviable es obligatorio");
    }

    if (typeof enviable.out !== "function") {
      throw new Error("El enviable debe implementar out");
    }

    const salida = enviable.out();
    if (typeof salida !== "string") {
      throw new Error("JsonEnvio requiere que Enviable.out() devuelva string");
    }

    return salida;
  }

  getTipoPayload(): string {
    return "json-string";
  }
}
