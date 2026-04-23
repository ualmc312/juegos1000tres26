import {
    Enviable,
    FetchApiConexion,
    JsonEnvio,
    JsonRecibo,
    Traductor,
} from './comunicacion/core.js';

const COMANDO_REGISTRAR_JUGADOR = 'REGISTRAR_JUGADOR';
const COMANDO_INICIAR_RONDA = 'INICIAR_RONDA';
const COMANDO_ACTUALIZAR_BORRADOR = 'ACTUALIZAR_BORRADOR';
const COMANDO_ENVIAR_RESPUESTA = 'ENVIAR_RESPUESTA';
const COMANDO_ELEGIR_RESPUESTA = 'ELEGIR_RESPUESTA';
const COMANDO_ESTADO_PREGUNTAS = 'ESTADO_PREGUNTAS';

const MAX_CARACTERES = 120;

const estadoMensaje = document.getElementById('estadoMensaje');
const faseBadge = document.getElementById('faseBadge');
const rondaLabel = document.getElementById('rondaLabel');
const tiempoLabel = document.getElementById('tiempoLabel');
const preguntaActual = document.getElementById('preguntaActual');
const jugadorElegidoLabel = document.getElementById('jugadorElegidoLabel');
const pendientesLabel = document.getElementById('pendientesLabel');
const seccionResponder = document.getElementById('seccionResponder');
const seccionElegir = document.getElementById('seccionElegir');
const respuestaInput = document.getElementById('respuestaInput');
const contadorCaracteres = document.getElementById('contadorCaracteres');
const btnEnviarRespuesta = document.getElementById('btnEnviarRespuesta');
const btnIniciarRonda = document.getElementById('btnIniciarRonda');
const listaOpciones = document.getElementById('listaOpciones');
const tablaMarcador = document.getElementById('tablaMarcador');
const nombreJugadorInput = document.getElementById('nombreJugadorInput');
const btnUnirse = document.getElementById('btnUnirse');

let conexion = null;
let traductor = null;
let recepcionActiva = false;
let borradorTimer = null;
let estadoActual = null;

const playerId = obtenerPlayerId();
let nombreJugadorActual = '';

class EventoPreguntasEnviable extends Enviable {
    constructor(data) {
        super();
        this.data = data;
    }

    out() {
        return JSON.stringify(this.data);
    }

    in(entrada) {
        this.data = JSON.parse(entrada);
    }
}

class EstadoPreguntasEvento {
    constructor(onEstado) {
        this.onEstado = onEstado;
    }

    async hacer(payload, _contexto) {
        if (typeof payload !== 'string') {
            return;
        }

        let data;
        try {
            data = JSON.parse(payload);
        } catch {
            return;
        }

        if (!data || data.comando !== COMANDO_ESTADO_PREGUNTAS) {
            return;
        }

        this.onEstado(data);
    }
}

function obtenerPrefijoApiPreguntas() {
    const pathname = (window.location.pathname || '').replace(/\/+$/, '');
    if (pathname.startsWith('/server/preguntas')) {
        return '/server/preguntas';
    }

    return '';
}

function obtenerPlayerId() {
    const key = 'preguntas.playerId';
    const existente = window.sessionStorage.getItem(key);
    if (existente) {
        return existente;
    }

    const nuevo = (window.crypto && typeof window.crypto.randomUUID === 'function')
        ? window.crypto.randomUUID()
        : `p-${Date.now()}-${Math.floor(Math.random() * 100000)}`;

    window.sessionStorage.setItem(key, nuevo);
    return nuevo;
}

function obtenerNombreInicialDesdeQuery() {
    const params = new URLSearchParams(window.location.search);
    const nombre = params.get('player');
    return typeof nombre === 'string' ? nombre.trim() : '';
}

function actualizarContador() {
    const texto = respuestaInput.value || '';
    contadorCaracteres.textContent = `${texto.length} / ${MAX_CARACTERES}`;
}

function setMensaje(texto, isWarning = false) {
    estadoMensaje.textContent = texto;
    estadoMensaje.classList.toggle('warning', Boolean(isWarning));
}

async function asegurarCanal() {
    if (traductor && conexion) {
        return;
    }

    const prefix = obtenerPrefijoApiPreguntas();

    conexion = new FetchApiConexion(
        `${prefix}/api/event`,
        `${prefix}/api/updates`,
        {
            pollingIntervalMs: 700,
        }
    );

    const recibo = new JsonRecibo().conEvento(
        COMANDO_ESTADO_PREGUNTAS,
        new EstadoPreguntasEvento(renderizarEstado)
    );

    traductor = new Traductor(conexion, new JsonEnvio(), recibo);
    await conexion.conectar();

    if (!recepcionActiva) {
        recepcionActiva = true;
        iniciarBucleRecepcion();
    }
}

async function iniciarBucleRecepcion() {
    while (recepcionActiva) {
        try {
            await traductor.recibirYProcesar();
        } catch (_error) {
            if (!recepcionActiva) {
                return;
            }
            await esperar(250);
        }
    }
}

async function enviarComando(comando, extra = {}) {
    if (!traductor) {
        throw new Error('Canal no inicializado');
    }

    const payload = {
        comando,
        jugadorId: playerId,
        nombreJugador: nombreJugadorActual || 'Jugador',
        ...extra,
    };

    await traductor.enviar(new EventoPreguntasEnviable(payload));
}

function renderizarEstado(estado) {
    estadoActual = estado;

    const fase = typeof estado.fase === 'string' ? estado.fase : 'ESPERANDO_JUGADORES';
    const ronda = Number.isFinite(estado.rondaActual) ? estado.rondaActual : 0;
    const tiempoMs = Number.isFinite(estado.tiempoRestanteMs) ? estado.tiempoRestanteMs : 0;
    const segundos = Math.ceil(Math.max(0, tiempoMs) / 1000);

    faseBadge.textContent = fase.replaceAll('_', ' ');
    rondaLabel.textContent = `Ronda ${ronda}`;
    tiempoLabel.textContent = `${segundos}s`;

    preguntaActual.textContent = estado.preguntaActual || 'Esperando partida...';

    const elegido = estado.jugadorElegido;
    const elegidoNombre = elegido && typeof elegido.nombreJugador === 'string'
        ? elegido.nombreJugador
        : '-';
    jugadorElegidoLabel.textContent = `Jugador elegido: ${elegidoNombre}`;

    const pendientes = Array.isArray(estado.respondedoresPendientes)
        ? estado.respondedoresPendientes.map((p) => p.nombreJugador).filter(Boolean)
        : [];
    pendientesLabel.textContent = pendientes.length > 0
        ? `Pendientes: ${pendientes.join(', ')}`
        : 'Pendientes: ninguno';

    setMensaje(estado.mensaje || 'Partida en curso');

    const soyElegido = Boolean(elegido && elegido.jugadorId === playerId);
    const responderVisible = fase === 'RESPONDIENDO' && !soyElegido;
    const elegirVisible = fase === 'ELEGIENDO' && soyElegido;

    seccionResponder.classList.toggle('hidden', !responderVisible);
    seccionElegir.classList.toggle('hidden', !elegirVisible && fase !== 'MOSTRANDO_RESULTADO');

    if (fase !== 'RESPONDIENDO') {
        btnEnviarRespuesta.disabled = true;
    } else {
        btnEnviarRespuesta.disabled = !responderVisible;
    }

    btnIniciarRonda.disabled = !Boolean(estado.puedeIniciarRonda);

    renderizarOpciones(estado, fase, soyElegido);
    renderizarMarcador(estado);
}

function renderizarOpciones(estado, fase, soyElegido) {
    const opciones = Array.isArray(estado.opciones) ? estado.opciones : [];

    if (opciones.length === 0) {
        listaOpciones.innerHTML = '<li class="opcion-item">No hay respuestas para mostrar.</li>';
        return;
    }

    listaOpciones.innerHTML = '';

    opciones.forEach((opcion, index) => {
        const item = document.createElement('li');
        item.className = 'opcion-item';

        if (opcion.seleccionada) {
            item.classList.add('ganadora');
        }

        if (!opcion.seleccionable) {
            item.classList.add('no-seleccionable');
        }

        const header = document.createElement('div');
        header.className = 'opcion-header';
        const etiqueta = document.createElement('span');
        etiqueta.textContent = `Opcion ${index + 1}`;
        header.appendChild(etiqueta);

        if (typeof opcion.autorNombre === 'string' && opcion.autorNombre) {
            const autor = document.createElement('span');
            autor.className = 'option-author';
            autor.textContent = `Autor: ${opcion.autorNombre}`;
            header.appendChild(autor);
        }

        const texto = document.createElement('p');
        texto.textContent = opcion.texto || '(respuesta vacia)';

        item.appendChild(header);
        item.appendChild(texto);

        if (fase === 'ELEGIENDO' && soyElegido) {
            const boton = document.createElement('button');
            boton.className = 'btn';
            boton.textContent = 'Seleccionar';
            boton.disabled = !opcion.seleccionable;
            boton.addEventListener('click', async () => {
                try {
                    await enviarComando(COMANDO_ELEGIR_RESPUESTA, { opcionId: opcion.opcionId });
                } catch (error) {
                    setMensaje(`No se pudo seleccionar opcion: ${error.message}`, true);
                }
            });
            item.appendChild(boton);
        }

        listaOpciones.appendChild(item);
    });
}

function renderizarMarcador(estado) {
    const marcador = Array.isArray(estado.marcador) ? estado.marcador : [];

    if (marcador.length === 0) {
        tablaMarcador.innerHTML = '<tr><td colspan="3">Sin jugadores</td></tr>';
        return;
    }

    tablaMarcador.innerHTML = marcador
        .map((jugador, index) => `
            <tr>
                <td>${index + 1}</td>
                <td>${escaparHtml(jugador.nombreJugador || 'Jugador')}</td>
                <td>${Number.isFinite(jugador.puntos) ? jugador.puntos : 0}</td>
            </tr>
        `)
        .join('');
}

function escaparHtml(texto) {
    return String(texto)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function esperar(ms) {
    return new Promise((resolve) => window.setTimeout(resolve, ms));
}

async function handleUnirse() {
    const nombre = (nombreJugadorInput.value || '').trim();
    if (!nombre) {
        setMensaje('Escribe tu nombre para unirte.', true);
        return;
    }

    nombreJugadorActual = nombre;
    nombreJugadorInput.value = nombre;

    try {
        await asegurarCanal();
        await enviarComando(COMANDO_REGISTRAR_JUGADOR, { nombreJugador: nombreJugadorActual });

        btnUnirse.disabled = true;
        nombreJugadorInput.disabled = true;
        setMensaje(`Conectado como ${nombreJugadorActual}`);
    } catch (error) {
        setMensaje(`Error al unirse: ${error.message}`, true);
    }
}

function programarEnvioBorrador() {
    if (!traductor || !estadoActual) {
        return;
    }

    const fase = estadoActual.fase;
    const elegido = estadoActual.jugadorElegido;
    const soyElegido = Boolean(elegido && elegido.jugadorId === playerId);

    if (fase !== 'RESPONDIENDO' || soyElegido) {
        return;
    }

    if (borradorTimer !== null) {
        clearTimeout(borradorTimer);
    }

    borradorTimer = window.setTimeout(async () => {
        try {
            await enviarComando(COMANDO_ACTUALIZAR_BORRADOR, {
                respuesta: respuestaInput.value,
            });
        } catch (_error) {
            // El borrador es opcional; no interrumpimos la UX por errores puntuales.
        }
    }, 350);
}

btnUnirse.addEventListener('click', () => {
    handleUnirse().catch((error) => setMensaje(error.message, true));
});

nombreJugadorInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
        event.preventDefault();
        handleUnirse().catch((error) => setMensaje(error.message, true));
    }
});

respuestaInput.addEventListener('input', () => {
    if ((respuestaInput.value || '').length > MAX_CARACTERES) {
        respuestaInput.value = respuestaInput.value.slice(0, MAX_CARACTERES);
    }

    actualizarContador();
    programarEnvioBorrador();
});

btnEnviarRespuesta.addEventListener('click', async () => {
    if (!traductor) {
        setMensaje('Debes unirte antes de responder.', true);
        return;
    }

    try {
        await enviarComando(COMANDO_ENVIAR_RESPUESTA, {
            respuesta: respuestaInput.value,
        });
        setMensaje('Respuesta enviada. Esperando al resto de jugadores...');
    } catch (error) {
        setMensaje(`No se pudo enviar la respuesta: ${error.message}`, true);
    }
});

btnIniciarRonda.addEventListener('click', async () => {
    if (!traductor) {
        setMensaje('Debes unirte antes de iniciar una ronda.', true);
        return;
    }

    try {
        await enviarComando(COMANDO_INICIAR_RONDA, {});
    } catch (error) {
        setMensaje(`No se pudo iniciar la ronda: ${error.message}`, true);
    }
});

window.addEventListener('beforeunload', () => {
    recepcionActiva = false;
});

(function init() {
    const nombreEnQuery = obtenerNombreInicialDesdeQuery();
    if (nombreEnQuery) {
        nombreJugadorInput.value = nombreEnQuery;
    }

    actualizarContador();
    setMensaje('Escribe tu nombre y pulsa Unirse para empezar.');
})();
