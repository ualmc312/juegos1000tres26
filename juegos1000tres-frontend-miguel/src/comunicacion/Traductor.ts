import { ContextoEvento } from "./ContextoEvento.js";
import { Enviable } from "./Enviable.js";
import { Conexion } from "./Conexion.js";
import { Envio } from "./Envio.js";
import { Recibo } from "./Recibo.js";

export class Traductor<PAYLOAD> {
  private readonly conexion: Conexion<PAYLOAD>;
  private readonly envio: Envio<PAYLOAD>;
  private readonly recibo: Recibo<PAYLOAD>;
  private readonly tipoPayload: string;

  constructor(conexion: Conexion<PAYLOAD>, envio: Envio<PAYLOAD>, recibo: Recibo<PAYLOAD>) {
    if (!conexion) {
      throw new Error("La conexion es obligatoria");
    }

    if (!envio) {
      throw new Error("La estrategia de envio es obligatoria");
    }

    if (!recibo) {
      throw new Error("La estrategia de recibo es obligatoria");
    }

    this.conexion = conexion;
    this.envio = envio;
    this.recibo = recibo;
    this.tipoPayload = this.conexion.getTipoPayload();

    this.validarCompatibilidadPayload();
  }

  traducirEnviableAFormato(enviable: Enviable): PAYLOAD {
    return this.envio.traducirEnviableAFormato(enviable);
  }

  async enviar(enviable: Enviable): Promise<void> {
    const payload = this.traducirEnviableAFormato(enviable);
    await this.conexion.enviar(payload);
  }

  async procesar(payload: PAYLOAD): Promise<PAYLOAD | undefined> {
    if (payload === undefined || payload === null) {
      throw new Error("El payload es obligatorio");
    }

    const contexto = new ContextoEvento();
    await this.recibo.procesar(payload, contexto);

    const respuesta = contexto.obtenerRespuesta();
    return respuesta ? this.traducirEnviableAFormato(respuesta) : undefined;
  }

  async recibirPayload(): Promise<PAYLOAD> {
    return this.conexion.recibir();
  }

  async recibirYProcesar(): Promise<PAYLOAD | undefined> {
    const payload = await this.recibirPayload();
    return this.procesar(payload);
  }

  async recibirProcesarYResponder(): Promise<PAYLOAD | undefined> {
    const respuesta = await this.recibirYProcesar();

    if (respuesta !== undefined) {
      await this.conexion.enviar(respuesta);
    }

    return respuesta;
  }

  getTipoPayload(): string {
    return this.tipoPayload;
  }

  private validarCompatibilidadPayload(): void {
    const tipoEnvio = this.envio.getTipoPayload();
    const tipoRecibo = this.recibo.getTipoPayload();

    if (this.tipoPayload !== tipoEnvio) {
      throw new Error("El tipo de payload de Envio no coincide con Conexion");
    }

    if (this.tipoPayload !== tipoRecibo) {
      throw new Error("El tipo de payload de Recibo no coincide con Conexion");
    }
  }
}
