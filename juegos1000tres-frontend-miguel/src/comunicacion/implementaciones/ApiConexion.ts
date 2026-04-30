import { Conexion } from "../Conexion.js";

type ApiConexionOptions = {
  salaId?: string;
  host?: string;
  puerto?: number;
  endpointSalaTemplate?: string;
  endpointActualizacionesSalaTemplate?: string;
  pollingIntervalMs?: number;
  payloadInicial?: string;
  fetchImpl?: typeof fetch;
};

type EsperaMensaje = {
  resolve: (payload: string) => void;
  reject: (reason?: unknown) => void;
};

export class ApiConexion extends Conexion<string> {
  private static readonly TIPO_COMUNICACION = "API";
  private static readonly TIPO_PAYLOAD = "json-string";
  private static readonly SALA_ID_DEFECTO = "sala-default";
  private static readonly HOST_DEFECTO = "127.0.0.1";
  private static readonly PUERTO_DEFECTO = 8081;
  private static readonly ENDPOINT_SALA_TEMPLATE_DEFECTO = "/api/salas/%s/eventos";
  private static readonly ENDPOINT_ACTUALIZACIONES_SALA_TEMPLATE_DEFECTO = "/api/salas/%s/actualizaciones";
  private static readonly POLLING_INTERVAL_MS_DEFECTO = 1000;
  private static readonly PAYLOAD_INICIAL_DEFECTO = "{}";

  private readonly salaId: string;
  private readonly endpointSala: string;
  private readonly endpointActualizacionesSala: string;
  private readonly urlEndpointSala: string;
  private readonly urlEndpointActualizacionesSala: string;
  private readonly pollingIntervalMs: number;
  private readonly payloadInicial: string;
  private readonly fetchImpl: typeof fetch;

  private conectada: boolean;
  private pollingTimer: ReturnType<typeof setInterval> | null;
  private pollingEnCurso: boolean;
  private colaMensajes: string[];
  private esperas: EsperaMensaje[];

  constructor(options: ApiConexionOptions = {}) {
    super();
    this.salaId = normalizarSalaId(options.salaId ?? ApiConexion.SALA_ID_DEFECTO);

    const host = normalizarHost(options.host ?? ApiConexion.HOST_DEFECTO);
    const puerto = normalizarPuerto(options.puerto ?? ApiConexion.PUERTO_DEFECTO);
    const endpointTemplate = normalizarTemplateEndpoint(
      options.endpointSalaTemplate ?? ApiConexion.ENDPOINT_SALA_TEMPLATE_DEFECTO,
      "endpointSalaTemplate"
    );
    const endpointActualizacionesTemplate = normalizarTemplateEndpoint(
      options.endpointActualizacionesSalaTemplate ?? ApiConexion.ENDPOINT_ACTUALIZACIONES_SALA_TEMPLATE_DEFECTO,
      "endpointActualizacionesSalaTemplate"
    );

    this.endpointSala = construirRutaSala(endpointTemplate, this.salaId);
    this.endpointActualizacionesSala = construirRutaSala(endpointActualizacionesTemplate, this.salaId);
    this.urlEndpointSala = `http://${host}:${puerto}${this.endpointSala}`;
    this.urlEndpointActualizacionesSala = `http://${host}:${puerto}${this.endpointActualizacionesSala}`;
    this.pollingIntervalMs = normalizarPollingInterval(
      options.pollingIntervalMs ?? ApiConexion.POLLING_INTERVAL_MS_DEFECTO
    );
    this.payloadInicial = normalizarPayloadInicial(options.payloadInicial ?? ApiConexion.PAYLOAD_INICIAL_DEFECTO);
    this.fetchImpl = options.fetchImpl ?? fetch;

    this.conectada = false;
    this.pollingTimer = null;
    this.pollingEnCurso = false;
    this.colaMensajes = [];
    this.esperas = [];
  }

  async conectar(): Promise<void> {
    if (this.conectada) {
      return;
    }

    this.conectada = true;
    this.iniciarPolling();
  }

  async desconectar(): Promise<void> {
    if (!this.conectada) {
      return;
    }

    this.conectada = false;
    this.detenerPolling();

    const error = new Error("Conexion API cerrada");
    while (this.esperas.length > 0) {
      const espera = this.esperas.shift();
      espera?.reject(error);
    }

    this.colaMensajes = [];
  }

  async enviar(payload: string): Promise<void> {
    this.validarConexionActiva();

    if (typeof payload !== "string") {
      throw new Error("El payload de API debe ser string");
    }

    const response = await this.fetchImpl(this.urlEndpointSala, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: payload,
    });

    if (!response.ok) {
      const detalle = await leerDetalleRespuesta(response);
      throw new Error(`Error API al enviar (${response.status}): ${detalle}`);
    }
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
    return ApiConexion.TIPO_PAYLOAD;
  }

  getTipoComunicacion(): string {
    return ApiConexion.TIPO_COMUNICACION;
  }

  getSalaId(): string {
    return this.salaId;
  }

  getCanalSala(): string {
    return this.endpointSala;
  }

  getCanalActualizacionesSala(): string {
    return this.endpointActualizacionesSala;
  }

  getUrlEndpointSala(): string {
    return this.urlEndpointSala;
  }

  getUrlEndpointActualizacionesSala(): string {
    return this.urlEndpointActualizacionesSala;
  }

  private validarConexionActiva(): void {
    if (!this.conectada) {
      throw new Error("La conexion API debe estar activa");
    }
  }

  private iniciarPolling(): void {
    this.detenerPolling();

    this.pollingTimer = setInterval(() => {
      this.ejecutarPolling().catch((error: unknown) => {
        if (!this.conectada) {
          return;
        }

        const detalle = error instanceof Error ? error.message : String(error);
        console.warn(`ApiConexion polling error [${this.salaId}]: ${detalle}`);
      });
    }, this.pollingIntervalMs);

    this.ejecutarPolling().catch((error: unknown) => {
      if (!this.conectada) {
        return;
      }

      const detalle = error instanceof Error ? error.message : String(error);
      console.warn(`ApiConexion first polling error [${this.salaId}]: ${detalle}`);
    });
  }

  private detenerPolling(): void {
    if (this.pollingTimer !== null) {
      clearInterval(this.pollingTimer);
      this.pollingTimer = null;
    }
  }

  private async ejecutarPolling(): Promise<void> {
    if (!this.conectada || this.pollingEnCurso) {
      return;
    }

    this.pollingEnCurso = true;
    try {
      const response = await this.fetchImpl(this.urlEndpointActualizacionesSala, {
        method: "GET",
        headers: {
          "Accept": "application/json",
        },
      });

      if (response.status === 204) {
        return;
      }

      if (!response.ok) {
        const detalle = await leerDetalleRespuesta(response);
        throw new Error(`Error API en polling (${response.status}): ${detalle}`);
      }

      const payload = (await response.text()).trim();
      if (!payload || payload === this.payloadInicial) {
        return;
      }

      this.encolarOMandarAEspera(payload);
    } finally {
      this.pollingEnCurso = false;
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
}

function normalizarSalaId(salaId: string): string {
  const id = salaId?.trim();
  if (!id) {
    throw new Error("El id de sala es obligatorio para ApiConexion");
  }

  return id;
}

function normalizarHost(host: string): string {
  const valor = host?.trim();
  if (!valor) {
    throw new Error("El host de ApiConexion es obligatorio");
  }

  return valor;
}

function normalizarPuerto(puerto: number): number {
  if (!Number.isInteger(puerto) || puerto < 1 || puerto > 65535) {
    throw new Error("El puerto de ApiConexion debe estar entre 1 y 65535");
  }

  return puerto;
}

function normalizarTemplateEndpoint(template: string, nombre: string): string {
  const valor = template?.trim();
  if (!valor) {
    throw new Error(`La propiedad ${nombre} es obligatoria`);
  }

  if (!valor.includes("%s")) {
    throw new Error(`La propiedad ${nombre} debe incluir %s para el id de sala`);
  }

  return valor;
}

function normalizarPayloadInicial(payloadInicial: string): string {
  const valor = payloadInicial?.trim();
  if (!valor) {
    throw new Error("El payload inicial de ApiConexion es obligatorio");
  }

  return valor;
}

function normalizarPollingInterval(pollingIntervalMs: number): number {
  if (!Number.isFinite(pollingIntervalMs) || pollingIntervalMs <= 0) {
    throw new Error("El pollingIntervalMs de ApiConexion debe ser un numero positivo");
  }

  return Math.floor(pollingIntervalMs);
}

function construirRutaSala(template: string, salaId: string): string {
  return template.replace("%s", encodeURIComponent(salaId));
}

async function leerDetalleRespuesta(response: Response): Promise<string> {
  const body = (await response.text()).trim();
  return body || "sin detalle";
}
