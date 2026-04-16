export abstract class Conexion<PAYLOAD> {
  static readonly TIPO_COMPROBAR_HOMOLOGO = "ComprobarHomologo";

  abstract conectar(): Promise<void>;

  abstract desconectar(): Promise<void>;

  abstract enviar(payload: PAYLOAD): Promise<void>;

  abstract recibir(): Promise<PAYLOAD>;

  abstract getTipoPayload(): string;

  abstract getTipoComunicacion(): string;

  getTipoVerificacion(): string {
    return Conexion.TIPO_COMPROBAR_HOMOLOGO;
  }

  comprobarHomologo(tipoComunicacionFront: string): boolean {
    if (!tipoComunicacionFront || !tipoComunicacionFront.trim()) {
      return false;
    }

    return this.getTipoComunicacion().toLowerCase() === tipoComunicacionFront.trim().toLowerCase();
  }
}
