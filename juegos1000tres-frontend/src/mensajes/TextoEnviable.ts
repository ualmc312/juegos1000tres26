import { Enviable } from "../comunicacion/Enviable.js";

export class TextoEnviable extends Enviable {
  texto: string;

  constructor(texto = "") {
    super();
    this.texto = texto;
  }

  out(): unknown {
    return JSON.stringify({ texto: this.texto });
  }

  in(entrada: unknown): void {
    if (typeof entrada !== "string") {
      throw new Error("TextoEnviable.in requiere un string JSON");
    }

    const json = entrada;
    const data = JSON.parse(json) as { texto?: unknown };
    this.texto = typeof data.texto === "string" ? data.texto : "";
  }
}
