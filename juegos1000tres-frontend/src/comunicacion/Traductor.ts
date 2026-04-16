import { Enviable, type EnviableConstructor } from "./Enviable.js";
import { Conexion } from "./Conexion.js";
import { Envio } from "./Envio.js";
import { Recibo } from "./Recibo.js";

type JuegoReceptor = {
  procesarMensajeEntrante(mensaje: Enviable): Promise<void> | void;
};

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

  traducirFormatoAEnviable<T extends Enviable>(
    payload: PAYLOAD,
    tipoEnviable: EnviableConstructor<T>
  ): T {
    return this.recibo.traducirFormatoAEnviable(payload, tipoEnviable);
  }

  async enviar(enviable: Enviable): Promise<void> {
    const payload = this.traducirEnviableAFormato(enviable);
    await this.conexion.enviar(payload);
  }

  async recibir<T extends Enviable>(tipoEnviable: EnviableConstructor<T>): Promise<T> {
    const payload = await this.conexion.recibir();
    return this.traducirFormatoAEnviable(payload, tipoEnviable);
  }

  async recibirYNotificarJuego<T extends Enviable>(
    juegoConexion: JuegoReceptor,
    tipoEnviable: EnviableConstructor<T>
  ): Promise<T> {
    if (!juegoConexion) {
      throw new Error("El juego es obligatorio");
    }

    const mensaje = await this.recibir(tipoEnviable);
    await juegoConexion.procesarMensajeEntrante(mensaje);
    return mensaje;
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
