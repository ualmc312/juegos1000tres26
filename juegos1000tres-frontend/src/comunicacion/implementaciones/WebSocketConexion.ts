import { Conexion } from "../Conexion.js";

type EsperaMensaje = {
  resolve: (payload: string) => void;
  reject: (reason?: unknown) => void;
};

export class WebSocketConexion extends Conexion<string> {
  private conectada: boolean;
  private colaMensajes: string[];
  private esperas: EsperaMensaje[];

  constructor() {
    super();
    this.conectada = false;
    this.colaMensajes = [];
    this.esperas = [];
  }

  async conectar(): Promise<void> {
    this.conectada = true;
  }

  async desconectar(): Promise<void> {
    this.conectada = false;

    const error = new Error("Conexion WebSocket cerrada");
    while (this.esperas.length > 0) {
      const espera = this.esperas.shift();
      espera?.reject(error);
    }

    this.colaMensajes = [];
  }

  async enviar(payload: string): Promise<void> {
    this.validarConexionActiva();

    if (typeof payload !== "string") {
      throw new Error("El payload de WebSocket debe ser string");
    }

    if (this.esperas.length > 0) {
      const espera = this.esperas.shift();
      espera?.resolve(payload);
      return;
    }

    this.colaMensajes.push(payload);
  }

  async recibir(): Promise<string> {
    this.validarConexionActiva();

    if (this.colaMensajes.length > 0) {
      return this.colaMensajes.shift() as string;
    }

    return new Promise<string>((resolve, reject) => {
      this.esperas.push({ resolve, reject });
    });
  }

  getTipoPayload(): string {
    return "json-string";
  }

  getTipoComunicacion(): string {
    return "WEBSOCKET";
  }

  private validarConexionActiva(): void {
    if (!this.conectada) {
      throw new Error("La conexion WebSocket debe estar activa");
    }
  }
}
