export class Enviable {
  out() {
    throw new Error("Debes implementar out() en tu Enviable");
  }

  in(_entrada) {
    throw new Error("Debes implementar in(entrada) en tu Enviable");
  }
}

export class ContextoEvento {
  constructor() {
    this.respuesta = undefined;
  }

  enviar(enviable) {
    if (!enviable) {
      throw new Error("El enviable de respuesta es obligatorio");
    }

    if (this.respuesta !== undefined) {
      throw new Error("Solo se puede establecer una respuesta principal por evento");
    }

    this.respuesta = enviable;
  }

  tieneRespuesta() {
    return this.respuesta !== undefined;
  }

  obtenerRespuesta() {
    return this.respuesta;
  }
}

export class Conexion {
  async conectar() {
    throw new Error("Debes implementar conectar() en tu Conexion");
  }

  async desconectar() {
    throw new Error("Debes implementar desconectar() en tu Conexion");
  }

  async enviar(_payload) {
    throw new Error("Debes implementar enviar(payload) en tu Conexion");
  }

  async recibir() {
    throw new Error("Debes implementar recibir() en tu Conexion");
  }

  getTipoPayload() {
    throw new Error("Debes implementar getTipoPayload() en tu Conexion");
  }

  getTipoComunicacion() {
    throw new Error("Debes implementar getTipoComunicacion() en tu Conexion");
  }
}

export class Envio {
  traducirEnviableAFormato(_enviable) {
    throw new Error("Debes implementar traducirEnviableAFormato(enviable)");
  }

  getTipoPayload() {
    throw new Error("Debes implementar getTipoPayload() en tu Envio");
  }
}

export class Recibo {
  conEvento(_comando, _evento) {
    throw new Error("Debes implementar conEvento(comando, evento)");
  }

  async procesar(_payload, _contexto) {
    throw new Error("Debes implementar procesar(payload, contexto)");
  }

  getTipoPayload() {
    throw new Error("Debes implementar getTipoPayload() en tu Recibo");
  }
}

export class Traductor {
  constructor(conexion, envio, recibo) {
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

  traducirEnviableAFormato(enviable) {
    return this.envio.traducirEnviableAFormato(enviable);
  }

  async enviar(enviable) {
    const payload = this.traducirEnviableAFormato(enviable);
    await this.conexion.enviar(payload);
  }

  async procesar(payload) {
    if (payload === undefined || payload === null) {
      throw new Error("El payload es obligatorio");
    }

    const contexto = new ContextoEvento();
    await this.recibo.procesar(payload, contexto);

    const respuesta = contexto.obtenerRespuesta();
    return respuesta ? this.traducirEnviableAFormato(respuesta) : undefined;
  }

  async recibirPayload() {
    return this.conexion.recibir();
  }

  async recibirYProcesar() {
    const payload = await this.recibirPayload();
    return this.procesar(payload);
  }

  async recibirProcesarYResponder() {
    const respuesta = await this.recibirYProcesar();

    if (respuesta !== undefined) {
      await this.conexion.enviar(respuesta);
    }

    return respuesta;
  }

  getTipoPayload() {
    return this.tipoPayload;
  }

  validarCompatibilidadPayload() {
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

export class JsonEnvio extends Envio {
  traducirEnviableAFormato(enviable) {
    if (!enviable) {
      throw new Error("El enviable es obligatorio");
    }

    if (typeof enviable.out !== "function") {
      throw new Error("El enviable debe implementar out");
    }

    const salida = enviable.out();
    if (typeof salida !== "string") {
      throw new Error("JsonEnvio requiere que Enviable.out() devuelva string");
    }

    return salida;
  }

  getTipoPayload() {
    return "json-string";
  }
}

export class JsonRecibo extends Recibo {
  constructor(eventosPorComando = new Map()) {
    super();
    this.eventosPorComando = new Map(eventosPorComando);
  }

  conEvento(comando, evento) {
    const comandoNormalizado = this.normalizarComando(comando);

    if (!evento) {
      throw new Error("El evento es obligatorio");
    }

    const siguienteMapa = new Map(this.eventosPorComando);
    siguienteMapa.set(comandoNormalizado, evento);
    return new JsonRecibo(siguienteMapa);
  }

  async procesar(payload, contexto) {
    if (!contexto) {
      throw new Error("El contexto de evento es obligatorio");
    }

    if (typeof payload !== "string") {
      throw new Error("El payload JSON debe ser un string");
    }

    const comando = this.extraerComando(payload);
    const evento = this.eventosPorComando.get(this.normalizarComando(comando));

    if (!evento) {
      return;
    }

    await evento.hacer(payload, contexto);
  }

  getTipoPayload() {
    return "json-string";
  }

  extraerComando(payload) {
    if (!payload.trim()) {
      throw new Error("El payload entrante no puede estar vacio");
    }

    let objeto;
    try {
      objeto = JSON.parse(payload);
    } catch {
      throw new Error("El payload no es un JSON valido");
    }

    if (!objeto || typeof objeto !== "object") {
      throw new Error("El payload no representa un objeto JSON");
    }

    const comando = objeto.comando;
    if (typeof comando !== "string" || !comando.trim()) {
      throw new Error("El payload no incluye el campo comando");
    }

    return comando;
  }

  normalizarComando(comando) {
    if (!comando || !comando.trim()) {
      throw new Error("El comando del evento es obligatorio");
    }

    return comando.trim().toLowerCase();
  }
}

export class FetchApiConexion extends Conexion {
  constructor(endpointEventos = "/api/event", endpointActualizaciones = "/api/updates", options = {}) {
    super();
    this.endpointEventos = endpointEventos;
    this.endpointActualizaciones = endpointActualizaciones;
    const playerId = typeof options.playerId === "string" ? options.playerId.trim() : "";
    const pollingQueryValue = typeof options.pollingQueryValue === "string"
      ? options.pollingQueryValue.trim()
      : "";
    const pollingQueryKey = typeof options.pollingQueryKey === "string"
      ? options.pollingQueryKey.trim()
      : "";

    this.pollingQueryValue = pollingQueryValue || playerId;
    this.pollingQueryKey = pollingQueryKey || "playerId";

    this.pollingIntervalMs = Number.isFinite(options.pollingIntervalMs) && options.pollingIntervalMs > 0
      ? Math.floor(options.pollingIntervalMs)
      : 1000;
    this.conectada = false;
    this.colaMensajes = [];
    this.esperas = [];
    this.pollingTimer = null;
    this.pollingEnCurso = false;
  }

  async conectar() {
    if (this.conectada) {
      return;
    }

    this.conectada = true;
    this.iniciarPolling();
  }

  async desconectar() {
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

  async enviar(payload) {
    this.validarConexionActiva();

    if (typeof payload !== "string") {
      throw new Error("El payload de API debe ser string");
    }

    const response = await fetch(this.endpointEventos, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: payload,
    });

    const responseText = await response.text();
    if (!response.ok) {
      throw new Error(`Error API ${response.status}: ${responseText || "sin detalle"}`);
    }
  }

  async recibir() {
    this.validarConexionActiva();

    if (this.colaMensajes.length > 0) {
      return this.colaMensajes.shift();
    }

    return new Promise((resolve, reject) => {
      this.esperas.push({ resolve, reject });
    });
  }

  getTipoPayload() {
    return "json-string";
  }

  getTipoComunicacion() {
    return "API";
  }

  validarConexionActiva() {
    if (!this.conectada) {
      throw new Error("La conexion API debe estar activa");
    }
  }

  construirUrlActualizaciones() {
    if (!this.pollingQueryValue) {
      return this.endpointActualizaciones;
    }

    const separator = this.endpointActualizaciones.includes("?") ? "&" : "?";
    return `${this.endpointActualizaciones}${separator}${encodeURIComponent(this.pollingQueryKey)}=${encodeURIComponent(this.pollingQueryValue)}`;
  }

  iniciarPolling() {
    this.detenerPolling();

    this.pollingTimer = setInterval(() => {
      this.ejecutarPolling().catch((error) => {
        if (!this.conectada) {
          return;
        }
        console.warn("Error de polling API:", error);
      });
    }, this.pollingIntervalMs);

    this.ejecutarPolling().catch((error) => {
      if (!this.conectada) {
        return;
      }
      console.warn("Error inicial de polling API:", error);
    });
  }

  detenerPolling() {
    if (this.pollingTimer !== null) {
      clearInterval(this.pollingTimer);
      this.pollingTimer = null;
    }
  }

  async ejecutarPolling() {
    if (!this.conectada || this.pollingEnCurso) {
      return;
    }

    this.pollingEnCurso = true;
    try {
      const response = await fetch(this.construirUrlActualizaciones(), {
        method: "GET",
        headers: {
          "Accept": "application/json",
        },
      });

      if (response.status === 204) {
        return;
      }

      const responseText = await response.text();
      if (!response.ok) {
        throw new Error(`Error API ${response.status}: ${responseText || "sin detalle"}`);
      }

      const payload = (responseText || "").trim();
      if (!payload) {
        return;
      }

      this.encolarOMandarAEspera(payload);
    } finally {
      this.pollingEnCurso = false;
    }
  }

  encolarOMandarAEspera(payload) {
    if (this.esperas.length > 0) {
      const espera = this.esperas.shift();
      espera?.resolve(payload);
      return;
    }

    this.colaMensajes.push(payload);
  }
}
