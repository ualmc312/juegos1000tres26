import { Enviable } from "./Enviable.js";

export class ContextoEvento {
  private respuesta?: Enviable;

  enviar(enviable: Enviable): void {
    if (!enviable) {
      throw new Error("El enviable de respuesta es obligatorio");
    }

    if (this.respuesta) {
      throw new Error("Solo se puede establecer una respuesta principal por evento");
    }

    this.respuesta = enviable;
  }

  tieneRespuesta(): boolean {
    return this.respuesta !== undefined;
  }

  obtenerRespuesta(): Enviable | undefined {
    return this.respuesta;
  }
}
