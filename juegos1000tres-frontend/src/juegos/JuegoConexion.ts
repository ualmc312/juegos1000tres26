import { Enviable, type EnviableConstructor } from "../comunicacion/Enviable.js";
import { Traductor } from "../comunicacion/Traductor.js";

export class JuegoConexion<PAYLOAD> {
  protected readonly traductor: Traductor<PAYLOAD>;
  private escuchando: boolean;

  constructor(traductor: Traductor<PAYLOAD>) {
    if (!traductor) {
      throw new Error("El traductor es obligatorio");
    }

    this.traductor = traductor;
    this.escuchando = false;
  }

  async enviar(enviable: Enviable): Promise<void> {
    await this.traductor.enviar(enviable);
  }

  async recibir<T extends Enviable>(tipoEnviable: EnviableConstructor<T>): Promise<T> {
    return this.traductor.recibir(tipoEnviable);
  }

  async iniciarEscucha<T extends Enviable>(tipoEnviable: EnviableConstructor<T>): Promise<void> {
    if (this.escuchando) {
      return;
    }

    this.escuchando = true;

    while (this.escuchando) {
      try {
        await this.traductor.recibirYNotificarJuego(this, tipoEnviable);
      } catch (error) {
        if (!this.escuchando) {
          return;
        }

        throw error;
      }
    }
  }

  detenerEscucha(): void {
    this.escuchando = false;
  }

  async procesarMensajeEntrante(_mensaje: Enviable): Promise<void> {
    throw new Error("Debes implementar procesarMensajeEntrante en tu juego concreto");
  }
}
