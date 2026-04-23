import { Conexion } from "../Conexion.js";
import WebSocket, { RawData } from "ws";

type EsperaMensaje = {
  resolve: (payload: string) => void;
  reject: (reason?: unknown) => void;
};

type WebSocketConexionOptions = {
  salaId?: string;
  host?: string;
  puerto?: number;
  canalSalaTemplate?: string;
  payloadVacio?: string;
  webSocketFactory?: (url: string) => WebSocket;
};

export class WebSocketConexion extends Conexion<string> {
  private static readonly TIPO_COMUNICACION = "WEBSOCKET";
  private static readonly TIPO_PAYLOAD = "json-string";
  private static readonly SALA_ID_DEFECTO = "sala-default";
  private static readonly HOST_DEFECTO = "127.0.0.1";
  private static readonly PUERTO_DEFECTO = 8091;
  private static readonly CANAL_SALA_TEMPLATE_DEFECTO = "/ws/salas/%s";
  private static readonly PAYLOAD_VACIO_DEFECTO = "{}";

  private readonly salaId: string;
  private readonly canalSala: string;
  private readonly urlCanalSala: string;
  private readonly payloadVacio: string;
  private readonly webSocketFactory: (url: string) => WebSocket;

  private conectada: boolean;
  private socket: WebSocket | null;
  private colaMensajes: string[];
  private esperas: EsperaMensaje[];

  constructor(options: WebSocketConexionOptions = {}) {
    super();
    this.salaId = normalizarSalaId(options.salaId ?? WebSocketConexion.SALA_ID_DEFECTO);

    const host = normalizarHost(options.host ?? WebSocketConexion.HOST_DEFECTO);
    const puerto = normalizarPuerto(options.puerto ?? WebSocketConexion.PUERTO_DEFECTO);
    const canalTemplate = normalizarTemplateCanal(
      options.canalSalaTemplate ?? WebSocketConexion.CANAL_SALA_TEMPLATE_DEFECTO,
      "canalSalaTemplate"
    );

    this.canalSala = construirRutaSala(canalTemplate, this.salaId);
    this.urlCanalSala = `ws://${host}:${puerto}${this.canalSala}`;
    this.payloadVacio = normalizarPayloadVacio(options.payloadVacio ?? WebSocketConexion.PAYLOAD_VACIO_DEFECTO);
    this.webSocketFactory = options.webSocketFactory ?? ((url) => new WebSocket(url));

    this.conectada = false;
    this.socket = null;
    this.colaMensajes = [];
    this.esperas = [];
  }

  async conectar(): Promise<void> {
    if (this.conectada) {
      return;
    }

    const socket = this.webSocketFactory(this.urlCanalSala);
    await esperarAperturaSocket(socket);

    this.socket = socket;
    this.conectada = true;

    socket.on("message", (data: RawData) => {
      const payload = rawDataAString(data, this.payloadVacio);
      this.encolarOMandarAEspera(payload);
    });

    socket.on("close", () => {
      this.conectada = false;
      this.socket = null;
      this.rechazarEsperas(new Error("Conexion WebSocket cerrada"));
    });

    socket.on("error", (error: Error) => {
      this.rechazarEsperas(new Error(`Error en WebSocket: ${error.message}`));
    });
  }

  async desconectar(): Promise<void> {
    const socket = this.socket;

    this.conectada = false;
    this.socket = null;

    if (socket) {
      await cerrarSocket(socket);
    }

    this.rechazarEsperas(new Error("Conexion WebSocket cerrada"));
    this.colaMensajes = [];
  }

  async enviar(payload: string): Promise<void> {
    this.validarConexionActiva();

    if (typeof payload !== "string") {
      throw new Error("El payload de WebSocket debe ser string");
    }

    const socket = this.socket;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      throw new Error("La conexion WebSocket no esta abierta para enviar");
    }

    await enviarPorSocket(socket, payload);
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
    return WebSocketConexion.TIPO_PAYLOAD;
  }

  getTipoComunicacion(): string {
    return WebSocketConexion.TIPO_COMUNICACION;
  }

  getSalaId(): string {
    return this.salaId;
  }

  getCanalSala(): string {
    return this.canalSala;
  }

  getUrlCanalSala(): string {
    return this.urlCanalSala;
  }

  private validarConexionActiva(): void {
    if (!this.conectada) {
      throw new Error("La conexion WebSocket debe estar activa");
    }
  }

  private encolarOMandarAEspera(payload: string): void {
    if (this.esperas.length > 0) {
      const espera = this.esperas.shift();
      espera?.resolve(payload);
      return;
    }

    this.colaMensajes.push(payload);
  }

  private rechazarEsperas(error: Error): void {
    while (this.esperas.length > 0) {
      const espera = this.esperas.shift();
      espera?.reject(error);
    }
  }
}

function normalizarSalaId(salaId: string): string {
  const id = salaId?.trim();
  if (!id) {
    throw new Error("El id de sala es obligatorio para WebSocketConexion");
  }

  return id;
}

function normalizarHost(host: string): string {
  const valor = host?.trim();
  if (!valor) {
    throw new Error("El host de WebSocketConexion es obligatorio");
  }

  return valor;
}

function normalizarPuerto(puerto: number): number {
  if (!Number.isInteger(puerto) || puerto < 1 || puerto > 65535) {
    throw new Error("El puerto de WebSocketConexion debe estar entre 1 y 65535");
  }

  return puerto;
}

function normalizarTemplateCanal(template: string, nombre: string): string {
  const valor = template?.trim();
  if (!valor) {
    throw new Error(`La propiedad ${nombre} es obligatoria`);
  }

  if (!valor.includes("%s")) {
    throw new Error(`La propiedad ${nombre} debe incluir %s para el id de sala`);
  }

  return valor;
}

function construirRutaSala(template: string, salaId: string): string {
  return template.replace("%s", encodeURIComponent(salaId));
}

function normalizarPayloadVacio(payload: string): string {
  const valor = payload?.trim();
  if (!valor) {
    throw new Error("El payload vacio de WebSocketConexion es obligatorio");
  }

  return valor;
}

function rawDataAString(data: RawData, payloadVacio: string): string {
  if (typeof data === "string") {
    return data;
  }

  if (Buffer.isBuffer(data)) {
    return data.toString("utf-8");
  }

  if (Array.isArray(data)) {
    return Buffer.concat(data).toString("utf-8");
  }

  if (data instanceof ArrayBuffer) {
    return Buffer.from(data).toString("utf-8");
  }

  return payloadVacio;
}

function esperarAperturaSocket(socket: WebSocket): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    const onOpen = (): void => {
      cleanup();
      resolve();
    };

    const onError = (error: Error): void => {
      cleanup();
      reject(new Error(`No se pudo abrir el WebSocket: ${error.message}`));
    };

    const cleanup = (): void => {
      socket.off("open", onOpen);
      socket.off("error", onError);
    };

    socket.on("open", onOpen);
    socket.on("error", onError);
  });
}

function cerrarSocket(socket: WebSocket): Promise<void> {
  return new Promise<void>((resolve) => {
    if (socket.readyState === WebSocket.CLOSED) {
      resolve();
      return;
    }

    const onClose = (): void => {
      cleanup();
      resolve();
    };

    const onError = (): void => {
      cleanup();
      resolve();
    };

    const cleanup = (): void => {
      socket.off("close", onClose);
      socket.off("error", onError);
    };

    socket.on("close", onClose);
    socket.on("error", onError);

    if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
      socket.close(1000, "Conexion cerrada por cliente");
      return;
    }

    cleanup();
    resolve();
  });
}

function enviarPorSocket(socket: WebSocket, payload: string): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    socket.send(payload, (error?: Error) => {
      if (error) {
        reject(new Error(`Error enviando por WebSocket: ${error.message}`));
        return;
      }

      resolve();
    });
  });
}
