import { Conexion } from "../Conexion.js";

export class ApiConexion extends Conexion<string> {
  private conectada: boolean;
  private ultimoMensaje: string;

  constructor() {
    super();
    this.conectada = false;
    this.ultimoMensaje = "{}";
  }

  async conectar(): Promise<void> {
    this.conectada = true;
  }

  async desconectar(): Promise<void> {
    this.conectada = false;
  }

  async enviar(payload: string): Promise<void> {
    this.validarConexionActiva();

    if (typeof payload !== "string") {
      throw new Error("El payload de API debe ser string");
    }

    this.ultimoMensaje = payload;
  }

  async recibir(): Promise<string> {
    this.validarConexionActiva();
    return this.ultimoMensaje;
  }

  getTipoPayload(): string {
    return "json-string";
  }

  getTipoComunicacion(): string {
    return "API";
  }

  private validarConexionActiva(): void {
    if (!this.conectada) {
      throw new Error("La conexion API debe estar activa");
    }
  }
}
