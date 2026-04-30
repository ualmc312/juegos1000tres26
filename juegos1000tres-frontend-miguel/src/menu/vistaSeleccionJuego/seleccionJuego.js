/**
 * seleccionJuego.js
 * Lógica de la vista de selección de juego · Arcade Hub
 *
 * Responsabilidades:
 *  - Construir pills de género dinámicamente desde window.CATALOGO
 *  - Filtrar tarjetas por texto y por género
 *  - Abrir el juego seleccionado dentro de un <iframe> con modal de carga
 *  - Gestionar el botón "Volver al menú"
 */

'use strict';

/* ── Referencias al DOM ──────────────────────────────────────────────────── */
const searchInput   = document.getElementById('searchInput');
const genrePills    = document.getElementById('genrePills');
const gameGrid      = document.getElementById('gameGrid');
const noResults     = document.getElementById('noResults');
const loadingModal  = document.getElementById('loadingModal');
const loaderText    = document.getElementById('loaderText');
const cancelBtn     = document.getElementById('cancelBtn');
const gameFrame     = document.getElementById('gameFrame');
const gameIframe    = document.getElementById('gameIframe');
const playingLabel  = document.getElementById('playingLabel');

/* ── Estado ──────────────────────────────────────────────────────────────── */
let activeGenre     = 'all';    // género activo en los pills
let loadTimeout     = null;     // timeout de la carga del juego
let currentGameId   = null;     // ID del juego abierto actualmente

/* ── Inicialización ──────────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
    construirPillsGenero();
    bindearTeclasCard();
    searchInput.addEventListener('input', filtrarJuegos);

    // Manejo de Deep Linking (Carga inicial si la URL lo indica)
    if (window.JUEGO_INICIAL && window.JUEGO_INICIAL !== 'None' && window.JUEGO_INICIAL !== '') {
        const juego = (window.CATALOGO || []).find(j => j.id === window.JUEGO_INICIAL);
        if (juego) {
            lanzarJuego(juego.id, juego.url, true);
        }
    }

    // Escuchar navegación del navegador (Atrás / Adelante)
    window.addEventListener('popstate', (event) => {
        if (event.state && event.state.juegoId) {
            const juego = (window.CATALOGO || []).find(j => j.id === event.state.juegoId);
            if (juego) lanzarJuego(juego.id, juego.url, true);
        } else {
            volverAlMenu(true);
        }
    });
});

/**
 * Construye dinámicamente los pills de género a partir del catálogo global.
 * El pill "Todos" ya viene en el HTML; aquí añadimos uno por cada género único.
 */
function construirPillsGenero() {
    if (!window.CATALOGO || !Array.isArray(window.CATALOGO)) return;

    const generos = [...new Set(window.CATALOGO.map(j => j.genero))].sort();

    generos.forEach(genero => {
        const btn = document.createElement('button');
        btn.className = 'pill';
        btn.textContent = genero;
        btn.dataset.genre = genero;
        btn.id = `pill-${genero.toLowerCase().replace(/\s+/g, '-')}`;
        btn.addEventListener('click', () => seleccionarGenero(genero, btn));
        genrePills.appendChild(btn);
    });

    // Activar el pill "Todos" al inicio
    document.getElementById('pill-all').addEventListener('click', () => {
        seleccionarGenero('all', document.getElementById('pill-all'));
    });
}

/* ── Filtrado ────────────────────────────────────────────────────────────── */

/**
 * Cambia el género activo y re-aplica el filtro combinado.
 * @param {string} genre - ID del género o 'all'
 * @param {HTMLButtonElement} pillEl - Elemento pill clicado
 */
function seleccionarGenero(genre, pillEl) {
    activeGenre = genre;

    // Actualizar estilos de pills
    document.querySelectorAll('.pill').forEach(p => p.classList.remove('active'));
    pillEl.classList.add('active');

    filtrarJuegos();
}

/**
 * Aplica el filtro combinado: texto del input + género activo.
 * Muestra/oculta tarjetas y el mensaje "sin resultados".
 */
function filtrarJuegos() {
    const busqueda = searchInput.value.trim().toLowerCase();
    const cards = gameGrid.querySelectorAll('.game-card');
    let visibles = 0;

    cards.forEach(card => {
        const titulo  = (card.dataset.title  || '').toLowerCase();
        const genero  = (card.dataset.genre   || '').toLowerCase();

        const coincideTexto  = !busqueda || titulo.includes(busqueda);
        const coincideGenero = activeGenre === 'all' || genero === activeGenre.toLowerCase();

        const mostrar = coincideTexto && coincideGenero;
        card.classList.toggle('hidden', !mostrar);
        if (mostrar) visibles++;
    });

    noResults.classList.toggle('hidden', visibles > 0);
}

/* ── Navegación por teclado en las tarjetas ──────────────────────────────── */
function bindearTeclasCard() {
    document.addEventListener('keydown', (e) => {
        if (e.target.classList.contains('game-card') && (e.key === 'Enter' || e.key === ' ')) {
            e.preventDefault();
            const playBtn = e.target.querySelector('.play-btn');
            if (playBtn) playBtn.click();
        }
    });
}

/* ── Lanzamiento del juego ───────────────────────────────────────────────── */

/**
 * Muestra el modal de carga y abre el iframe con la ruta del juego.
 * @param {string} juegoId      - ID del juego (e.g. 'space_invaders')
 * @param {string} url          - Ruta relativa del juego (e.g. '/server/space_invaders')
 * @param {boolean} skipHistory - Si es true, no se añade entrada al historial
 */
function lanzarJuego(juegoId, url, skipHistory = false) {
    if (currentGameId === juegoId) return; // Ya estamos en este juego

    const juego = (window.CATALOGO || []).find(j => j.id === juegoId);
    const titulo = juego ? juego.titulo : juegoId;
    currentGameId = juegoId;

    // Actualizar historial
    if (!skipHistory) {
        history.pushState({ juegoId }, '', `/juego/${juegoId}`);
    }

    // Mostrar modal
    loaderText.textContent = `Iniciando ${titulo}…`;
    loadingModal.classList.remove('hidden');
    playingLabel.textContent = `Jugando a ${titulo}`;

    // Intentar cargar durante máx. 10 segundos
    loadTimeout = setTimeout(() => {
        cerrarModal();
        alert(`No se pudo cargar "${titulo}". Revisa la consola o el servidor.`);
    }, 10000);

    // Lanzamos el iframe
    gameIframe.src = url;

    gameIframe.onload = () => {
        clearTimeout(loadTimeout);
        cerrarModal();
        gameFrame.classList.remove('hidden');
        document.body.style.overflow = 'hidden';
    };

    gameIframe.onerror = () => {
        clearTimeout(loadTimeout);
        cerrarModal();
        alert(`Error al cargar "${titulo}".`);
    };
}

/**
 * Cancela la carga en curso y cierra el modal.
 */
function cancelarCarga() {
    clearTimeout(loadTimeout);
    gameIframe.src = '';
    cerrarModal();
}

/**
 * Oculta el modal de carga.
 */
function cerrarModal() {
    loadingModal.classList.add('hidden');
}

/**
 * Cierra el iframe del juego y vuelve al menú principal.
 * @param {boolean} skipHistory - Si es true, no se añade entrada al historial
 */
function volverAlMenu(skipHistory = false) {
    currentGameId = null;

    if (!skipHistory) {
        history.pushState({}, '', '/');
    }

    gameIframe.src = '';
    gameFrame.classList.add('hidden');
    document.body.style.overflow = '';
}

/**
 * Alias de volverAlMenu solicitado por el usuario.
 */
function acabar() {
    volverAlMenu();
}

/* ── Exportar funciones globales (llamadas desde HTML onclick) ───────────── */
window.lanzarJuego    = lanzarJuego;
window.cancelarCarga  = cancelarCarga;
window.volverAlMenu   = volverAlMenu;
window.acabar         = acabar;
