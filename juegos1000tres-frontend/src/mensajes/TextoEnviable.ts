import { Enviable } from "../comunicacion/Enviable.js";

export class TextoEnviable extends Enviable {
  texto: string;

  constructor(texto = "") {
    super();
    this.texto = texto;
  }

  toJson(): string {
    return JSON.stringify({ texto: this.texto });
  }

  fromJson(json: string): void {
    const data = JSON.parse(json) as { texto?: unknown };
    this.texto = typeof data.texto === "string" ? data.texto : "";
  }
}
