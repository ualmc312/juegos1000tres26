import {
    FetchApiConexion,
    JsonEnvio,
    JsonRecibo,
    Traductor,
} from './comunicacion/core.js';

const COMANDO_ESTADO_PREGUNTAS = 'ESTADO_PREGUNTAS';

const pantallaEstado = document.getElementById('pantallaEstado');
const pantallaRonda = document.getElementById('pantallaRonda');
const pantallaJugadorElegido = document.getElementById('pantallaJugadorElegido');
const pantallaPregunta = document.getElementById('pantallaPregunta');
const pantallaOpciones = document.getElementById('pantallaOpciones');
const pantallaTablaMarcador = document.getElementById('pantallaTablaMarcador');

let recepcionActiva = false;

class EstadoPreguntasPantallaEvento {
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

function renderizarEstado(estado) {
    const fase = typeof estado.fase === 'string' ? estado.fase.replaceAll('_', ' ') : '-';
    pantallaEstado.textContent = `${estado.mensaje || 'Sin mensaje'} | Fase: ${fase}`;

    const ronda = Number.isFinite(estado.rondaActual) ? estado.rondaActual : 0;
    pantallaRonda.textContent = `Ronda ${ronda}`;

    const elegidoNombre = estado.jugadorElegido && estado.jugadorElegido.nombreJugador
        ? estado.jugadorElegido.nombreJugador
        : '-';
    pantallaJugadorElegido.textContent = `Jugador elegido: ${elegidoNombre}`;

    pantallaPregunta.textContent = estado.preguntaActual || 'Sin pregunta activa';

    renderizarOpciones(estado);
    renderizarMarcador(estado);
}

function renderizarOpciones(estado) {
    const opciones = Array.isArray(estado.opciones) ? estado.opciones : [];

    if (opciones.length === 0) {
        pantallaOpciones.innerHTML = '<li class="opcion-item">Aun no hay respuestas.</li>';
        return;
    }

    pantallaOpciones.innerHTML = opciones
        .map((opcion, index) => {
            const ganadoraClass = opcion.seleccionada ? ' ganadora' : '';
            const autor = opcion.autorNombre
                ? `<span class="option-author">Autor: ${escaparHtml(opcion.autorNombre)}</span>`
                : '<span class="option-author">Autor oculto</span>';
            const etiquetaVacia = opcion.seleccionable ? '' : '<span class="warning">(No seleccionable)</span>';

            return `
                <li class="opcion-item${ganadoraClass}">
                    <div class="opcion-header">
                        <span>Opcion ${index + 1}</span>
                        ${autor}
                    </div>
                    <p>${escaparHtml(opcion.texto || '(respuesta vacia)')}</p>
                    ${etiquetaVacia}
                </li>
            `;
        })
        .join('');
}

function renderizarMarcador(estado) {
    const marcador = Array.isArray(estado.marcador) ? estado.marcador : [];

    if (marcador.length === 0) {
        pantallaTablaMarcador.innerHTML = '<tr><td colspan="3">Sin jugadores</td></tr>';
        return;
    }

    pantallaTablaMarcador.innerHTML = marcador
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

async function iniciarPantalla() {
    const conexion = new FetchApiConexion(
        `/server/preguntas/api/event`,
        `/server/preguntas/api/updates`,
        {
            pollingIntervalMs: 800,
        }
    );

    const recibo = new JsonRecibo().conEvento(
        COMANDO_ESTADO_PREGUNTAS,
        new EstadoPreguntasPantallaEvento(renderizarEstado)
    );

    const traductor = new Traductor(conexion, new JsonEnvio(), recibo);
    await conexion.conectar();

    recepcionActiva = true;
    while (recepcionActiva) {
        try {
            await traductor.recibirYProcesar();
        } catch (_error) {
            if (!recepcionActiva) {
                break;
            }
            await esperar(250);
        }
    }
}

window.addEventListener('beforeunload', () => {
    recepcionActiva = false;
});

iniciarPantalla().catch((error) => {
    pantallaEstado.textContent = `No se pudo iniciar la pantalla: ${error.message}`;
});
