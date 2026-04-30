import {
  Enviable,
  JsonEnvio,
  JsonRecibo,
  Traductor,
  WebSocketConexionNavegador,
} from "./comunicacion/core.js";

const COMANDO_ENVIAR_TEXTO = "ENVIAR_TEXTO";
const COMANDO_TEXTO_GLOBAL = "TEXTO_GLOBAL";

const estadoConexion = document.getElementById("estadoConexion");
const nombreJugadorInput = document.getElementById("nombreJugador");
const textoInput = document.getElementById("textoInput");
const enviarBtn = document.getElementById("enviarBtn");
const mensajesList = document.getElementById("mensajesList");

const STORAGE_ID = "prueba_ws_jugador_id";
const STORAGE_NAME = "prueba_ws_nombre";

let traductor = null;
let recepcionActiva = false;

class EnviarTextoEnviable extends Enviable {
  constructor(jugadorId, nombreJugador, texto) {
    super();
    this.jugadorId = jugadorId;
    this.nombreJugador = nombreJugador;
    this.texto = texto;
  }

  out() {
    return JSON.stringify({
      comando: COMANDO_ENVIAR_TEXTO,
      jugadorId: this.jugadorId,
      nombreJugador: this.nombreJugador,
      texto: this.texto,
    });
  }

  in(_entrada) {}
}

class EventoTextoGlobal {
  constructor(onTexto) {
    this.onTexto = onTexto;
  }

  async hacer(payload) {
    if (typeof payload !== "string") {
      return;
    }

    let data;
    try {
      data = JSON.parse(payload);
    } catch {
      return;
    }

    if (!data || data.comando !== COMANDO_TEXTO_GLOBAL) {
      return;
    }

    const texto = typeof data.texto === "string" ? data.texto.trim() : "";
    if (!texto) {
      return;
    }

    this.onTexto(texto);
  }
}

function setEstado(texto, esError = false) {
  estadoConexion.textContent = texto;
  estadoConexion.classList.toggle("error", esError);
}

function obtenerJugadorId() {
  const actual = localStorage.getItem(STORAGE_ID);
  if (actual && actual.trim()) {
    return actual;
  }

  const nuevo = (window.crypto && typeof window.crypto.randomUUID === "function")
    ? window.crypto.randomUUID()
    : `jug-${Date.now()}-${Math.floor(Math.random() * 99999)}`;

  localStorage.setItem(STORAGE_ID, nuevo);
  return nuevo;
}

function obtenerNombreInicial() {
  const params = new URLSearchParams(window.location.search || "");
  const nombreEnQuery = params.get("player");
  if (typeof nombreEnQuery === "string" && nombreEnQuery.trim()) {
    const limpio = nombreEnQuery.trim();
    localStorage.setItem(STORAGE_NAME, limpio);
    return limpio;
  }

  const actual = localStorage.getItem(STORAGE_NAME);
  if (actual && actual.trim()) {
    return actual.trim();
  }

  return "Jugador";
}

function agregarMensaje(texto) {
  const item = document.createElement("li");
  item.textContent = texto;

  if (mensajesList.firstElementChild && mensajesList.firstElementChild.classList.contains("muted")) {
    mensajesList.innerHTML = "";
  }

  mensajesList.appendChild(item);
}

function obtenerUrlCanalJugadores() {
  const urlTemplate = typeof window.PRUEBA_WEBSOCKET_URL_JUGADORES === "string"
    ? window.PRUEBA_WEBSOCKET_URL_JUGADORES.trim()
    : "";

  if (urlTemplate) {
    return urlTemplate;
  }

  const host = window.location.hostname || "127.0.0.1";
  return `ws://${host}:8091/ws/salas/prueba-websocket-jugadores`;
}

async function iniciar() {
  const jugadorId = obtenerJugadorId();
  nombreJugadorInput.value = obtenerNombreInicial();

  const urlJugadores = obtenerUrlCanalJugadores();
  if (!urlJugadores) {
    throw new Error("No hay URL WebSocket de jugadores configurada");
  }

  const recibo = new JsonRecibo().conEvento(
    COMANDO_TEXTO_GLOBAL,
    new EventoTextoGlobal(agregarMensaje)
  );

  const conexion = new WebSocketConexionNavegador(urlJugadores);
  traductor = new Traductor(conexion, new JsonEnvio(), recibo);

  await conexion.conectar();
  setEstado("Conectado");

  recepcionActiva = true;
  bucleRecepcion();

  const enviarActual = async () => {
    const texto = (textoInput.value || "").trim();
    const nombreJugador = (nombreJugadorInput.value || "").trim() || "Jugador";
    if (!texto) {
      return;
    }

    localStorage.setItem(STORAGE_NAME, nombreJugador);

    await traductor.enviar(new EnviarTextoEnviable(jugadorId, nombreJugador, texto));
    textoInput.value = "";
    textoInput.focus();
  };

  enviarBtn.addEventListener("click", () => {
    enviarActual().catch((error) => setEstado(`Error enviando: ${error.message}`, true));
  });

  textoInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      enviarActual().catch((error) => setEstado(`Error enviando: ${error.message}`, true));
    }
  });
}

async function bucleRecepcion() {
  while (recepcionActiva) {
    try {
      await traductor.recibirYProcesar();
    } catch (error) {
      if (!recepcionActiva) {
        break;
      }
      setEstado(`Conexion inestable: ${error.message}`, true);
      await esperar(300);
    }
  }
}

function esperar(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

window.addEventListener("beforeunload", () => {
  recepcionActiva = false;
});

iniciar().catch((error) => {
  setEstado(`No se pudo iniciar: ${error.message}`, true);
});
