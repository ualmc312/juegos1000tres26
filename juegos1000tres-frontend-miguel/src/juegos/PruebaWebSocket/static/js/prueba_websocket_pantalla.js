import {
  JsonEnvio,
  JsonRecibo,
  Traductor,
  WebSocketConexionNavegador,
} from "./comunicacion/core.js";

const COMANDO_ESTADO_PANTALLA = "ESTADO_PANTALLA";

const estadoConexion = document.getElementById("estadoConexion");
const jugadoresBoard = document.getElementById("jugadoresBoard");

let traductor = null;
let recepcionActiva = false;

class EventoEstadoPantalla {
  constructor(onEstado) {
    this.onEstado = onEstado;
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

    if (!data || data.comando !== COMANDO_ESTADO_PANTALLA) {
      return;
    }

    const jugadores = Array.isArray(data.jugadores) ? data.jugadores : [];
    this.onEstado(jugadores);
  }
}

function setEstado(texto, esError = false) {
  estadoConexion.textContent = texto;
  estadoConexion.classList.toggle("error", esError);
}

function obtenerUrlCanalPantalla() {
  const urlTemplate = typeof window.PRUEBA_WEBSOCKET_URL_PANTALLA === "string"
    ? window.PRUEBA_WEBSOCKET_URL_PANTALLA.trim()
    : "";

  if (urlTemplate) {
    return urlTemplate;
  }

  const host = window.location.hostname || "127.0.0.1";
  return `ws://${host}:8091/ws/salas/prueba-websocket-pantalla`;
}

function renderizarJugadores(jugadores) {
  if (!jugadores.length) {
    jugadoresBoard.innerHTML = '<p class="muted">Aun no hay actividad.</p>';
    return;
  }

  jugadoresBoard.innerHTML = "";

  for (const item of jugadores) {
    const nombre = typeof item.nombreJugador === "string" && item.nombreJugador.trim()
      ? item.nombreJugador.trim()
      : "Jugador";
    const palabras = Array.isArray(item.palabras) ? item.palabras : [];

    const card = document.createElement("article");
    card.className = "player-card";

    const title = document.createElement("h3");
    title.textContent = `${nombre} (${palabras.length})`;
    card.appendChild(title);

    if (!palabras.length) {
      const vacio = document.createElement("p");
      vacio.className = "muted";
      vacio.textContent = "Sin palabras aun.";
      card.appendChild(vacio);
    } else {
      const list = document.createElement("ul");
      list.className = "words";
      for (const palabra of palabras) {
        const li = document.createElement("li");
        li.textContent = typeof palabra === "string" ? palabra : String(palabra ?? "");
        list.appendChild(li);
      }
      card.appendChild(list);
    }

    jugadoresBoard.appendChild(card);
  }
}

async function iniciar() {
  const urlPantalla = obtenerUrlCanalPantalla();
  if (!urlPantalla) {
    throw new Error("No hay URL WebSocket de pantalla configurada");
  }

  const recibo = new JsonRecibo().conEvento(
    COMANDO_ESTADO_PANTALLA,
    new EventoEstadoPantalla(renderizarJugadores)
  );

  const conexion = new WebSocketConexionNavegador(urlPantalla);
  traductor = new Traductor(conexion, new JsonEnvio(), recibo);

  await conexion.conectar();
  setEstado("Conectado");

  recepcionActiva = true;
  bucleRecepcion();
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
