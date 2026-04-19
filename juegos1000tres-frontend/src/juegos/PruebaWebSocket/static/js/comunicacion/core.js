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
    if (!conexion || !envio || !recibo) {
      throw new Error("conexion, envio y recibo son obligatorios");
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

  validarCompatibilidadPayload() {
    if (this.tipoPayload !== this.envio.getTipoPayload()) {
      throw new Error("El tipo de payload de Envio no coincide con Conexion");
    }

    if (this.tipoPayload !== this.recibo.getTipoPayload()) {
      throw new Error("El tipo de payload de Recibo no coincide con Conexion");
    }
  }
}

export class JsonEnvio extends Envio {
  traducirEnviableAFormato(enviable) {
    if (!enviable || typeof enviable.out !== "function") {
      throw new Error("El enviable debe implementar out()");
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
    if (!comando || !comando.trim()) {
      throw new Error("El comando es obligatorio");
    }

    if (!evento) {
      throw new Error("El evento es obligatorio");
    }

    const siguienteMapa = new Map(this.eventosPorComando);
    siguienteMapa.set(comando.trim().toLowerCase(), evento);
    return new JsonRecibo(siguienteMapa);
  }

  async procesar(payload, contexto) {
    if (!contexto) {
      throw new Error("El contexto es obligatorio");
    }

    if (typeof payload !== "string" || !payload.trim()) {
      return;
    }

    let objeto;
    try {
      objeto = JSON.parse(payload);
    } catch {
      return;
    }

    const comando = typeof objeto.comando === "string" ? objeto.comando.trim().toLowerCase() : "";
    if (!comando) {
      return;
    }

    const evento = this.eventosPorComando.get(comando);
    if (!evento) {
      return;
    }

    await evento.hacer(payload, contexto);
  }

  getTipoPayload() {
    return "json-string";
  }
}

export class WebSocketConexionNavegador extends Conexion {
  constructor(urlCanal) {
    super();

    if (!urlCanal || !urlCanal.trim()) {
      throw new Error("La URL del canal WebSocket es obligatoria");
    }

    this.urlCanal = urlCanal.trim();
    this.socket = null;
    this.conectada = false;
    this.colaMensajes = [];
    this.esperas = [];
  }

  async conectar() {
    if (this.conectada && this.socket && this.socket.readyState === WebSocket.OPEN) {
      return;
    }

    await this.abrirSocket();
  }

  async desconectar() {
    this.conectada = false;

    if (this.socket) {
      this.socket.close(1000, "Cliente desconectado");
      this.socket = null;
    }

    const error = new Error("Conexion WebSocket cerrada");
    while (this.esperas.length > 0) {
      const espera = this.esperas.shift();
      espera?.reject(error);
    }

    this.colaMensajes = [];
  }

  async enviar(payload) {
    this.validarActiva();

    if (typeof payload !== "string") {
      throw new Error("El payload de WebSocket debe ser string");
    }

    this.socket.send(payload);
  }

  async recibir() {
    this.validarActiva();

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
    return "WEBSOCKET";
  }

  validarActiva() {
    if (!this.conectada || !this.socket || this.socket.readyState !== WebSocket.OPEN) {
      throw new Error("La conexion WebSocket no esta activa");
    }
  }

  abrirSocket() {
    return new Promise((resolve, reject) => {
      const socket = new WebSocket(this.urlCanal);

      const limpiar = () => {
        socket.removeEventListener("open", onOpen);
        socket.removeEventListener("error", onErrorInicial);
      };

      const onOpen = () => {
        limpiar();
        this.socket = socket;
        this.conectada = true;

        socket.addEventListener("message", (evento) => {
          const payload = typeof evento.data === "string" ? evento.data : String(evento.data ?? "");
          if (this.esperas.length > 0) {
            const espera = this.esperas.shift();
            espera?.resolve(payload);
            return;
          }
          this.colaMensajes.push(payload);
        });

        socket.addEventListener("close", () => {
          this.conectada = false;
          this.socket = null;

          const error = new Error("Conexion WebSocket cerrada por servidor");
          while (this.esperas.length > 0) {
            const espera = this.esperas.shift();
            espera?.reject(error);
          }
        });

        socket.addEventListener("error", () => {
          this.conectada = false;
        });

        resolve();
      };

      const onErrorInicial = () => {
        limpiar();
        reject(new Error("No se pudo abrir el canal WebSocket"));
      };

      socket.addEventListener("open", onOpen);
      socket.addEventListener("error", onErrorInicial);
    });
  }
}
