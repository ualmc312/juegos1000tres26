import { ContextoEvento } from "../ContextoEvento.js";
import { Evento } from "../Evento.js";
import { Recibo } from "../Recibo.js";

export class JsonRecibo extends Recibo<string> {
  private readonly eventosPorComando: Map<string, Evento<string>>;

  constructor(eventosPorComando: Map<string, Evento<string>> = new Map()) {
    super();
    this.eventosPorComando = new Map(eventosPorComando);
  }

  conEvento(comando: string, evento: Evento<string>): Recibo<string> {
    const comandoNormalizado = this.normalizarComando(comando);

    if (!evento) {
      throw new Error("El evento es obligatorio");
    }

    const siguienteMapa = new Map(this.eventosPorComando);
    siguienteMapa.set(comandoNormalizado, evento);
    return new JsonRecibo(siguienteMapa);
  }

  async procesar(payload: string, contexto: ContextoEvento): Promise<void> {
    if (!contexto) {
      throw new Error("El contexto de evento es obligatorio");
    }

    if (typeof payload !== "string") {
      throw new Error("El payload JSON debe ser string");
    }

    const comando = this.extraerComando(payload);
    const evento = this.eventosPorComando.get(this.normalizarComando(comando));

    if (!evento) {
      return;
    }

    await evento.hacer(payload, contexto);
  }

  getTipoPayload(): string {
    return "json-string";
  }

  private extraerComando(payload: string): string {
    if (!payload.trim()) {
      throw new Error("El payload entrante no puede estar vacio");
    }

    let objeto: unknown;
    try {
      objeto = JSON.parse(payload) as unknown;
    } catch {
      throw new Error("El payload no es un JSON valido");
    }

    if (!objeto || typeof objeto !== "object") {
      throw new Error("El payload no representa un objeto JSON");
    }

    const comando = (objeto as { comando?: unknown }).comando;
    if (typeof comando !== "string" || !comando.trim()) {
      throw new Error("El payload no incluye el campo comando");
    }

    return comando;
  }

  private normalizarComando(comando: string): string {
    if (!comando || !comando.trim()) {
      throw new Error("El comando del evento es obligatorio");
    }

    return comando.trim().toLowerCase();
  }
}
