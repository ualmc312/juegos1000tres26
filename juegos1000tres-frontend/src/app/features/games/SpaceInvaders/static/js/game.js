import {
    Enviable,
    FetchApiConexion,
    JsonEnvio,
    JsonRecibo,
    Traductor
} from './comunicacion/core.js';

const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');
const score1 = document.getElementById('score1');
const livesIcons = document.getElementById('livesIcons');
const livesText = document.getElementById('livesText');
const gameOverScreen = document.getElementById('gameOverScreen');
const finalScore = document.getElementById('finalScore');
const restartBtn = document.getElementById('restartBtn');
const roundText = document.getElementById('roundText');
const rankText = document.getElementById('rankText');
const playersText = document.getElementById('playersText');

const COLOR_PLAYER = '#39ff14';
const COLOR_ALIEN = '#ffffff';

const COMANDO_ACTUALIZAR_PUNTUACION = 'ACTUALIZAR_PUNTUACION';
const COMANDO_NOTIFICAR_MUERTE = 'NOTIFICAR_MUERTE';
const COMANDO_ESTADO_JUGADORES = 'ESTADO_JUGADORES';
const INTERVALO_ACTUALIZACION_PUNTUACION_MS = 2000;
const INTERVALO_POLLING_ESTADO_MS = 1000;

const PIXEL_SIZE = 3;

// Sprite Definitions (0 = empty, 1 = solid)
const SPRITE_PLAYER = [
    "00000100000",
    "00001110000",
    "00001110000",
    "01111111110",
    "11111111111",
    "11111111111",
    "11111111111",
    "11111111111"
];

// Top alien (Squid) - 8x8 actually, let's use 8x8 but padded
const SPRITE_ALIEN1_A = [
    "00011000",
    "00111100",
    "01111110",
    "11011011",
    "11111111",
    "00100100",
    "01011010",
    "10100101"
];
const SPRITE_ALIEN1_B = [
    "00011000",
    "00111100",
    "01111110",
    "11011011",
    "11111111",
    "00100100",
    "01000010",
    "00100100"
];

// Middle alien (Crab) - 11x8
const SPRITE_ALIEN2_A = [
    "00100000100",
    "00010001000",
    "00111111100",
    "01101110110",
    "11111111111",
    "10111111101",
    "10100000101",
    "00011011000"
];
const SPRITE_ALIEN2_B = [
    "00100000100",
    "10010001001",
    "10111111101",
    "11101110111",
    "11111111111",
    "01111111110",
    "00100000100",
    "01000000010"
];

// Bottom alien (Octopus) - 12x8
const SPRITE_ALIEN3_A = [
    "000011110000",
    "011111111110",
    "111111111111",
    "111001100111",
    "111111111111",
    "000110011000",
    "001101101100",
    "110000000011"
];
const SPRITE_ALIEN3_B = [
    "000011110000",
    "011111111110",
    "111111111111",
    "111001100111",
    "111111111111",
    "001110011100",
    "011001100110",
    "001100001100"
];

// Alien Explosion 13x8
const SPRITE_ALIEN_EXP = [
    "0000010000000",
    "0000101000000",
    "0000000000000",
    "0101000101000",
    "1000000000100",
    "0100000001000",
    "0001000100000",
    "0000010000000"
];

const SPRITE_SHIELD = [
    "000011111111111111110000",
    "000111111111111111111000",
    "001111111111111111111100",
    "011111111111111111111110",
    "111111111111111111111111",
    "111111111111111111111111",
    "111111111111111111111111",
    "111111111111111111111111",
    "111111111111111111111111",
    "111111111111111111111111",
    "111111111111111111111111",
    "111111111111111111111111",
    "111111110000000011111111",
    "111111100000000001111111",
    "111111000000000000111111",
    "111110000000000000011111"
];

function drawSprite(spriteArr, x, y, color) {
    ctx.fillStyle = color;
    for (let r = 0; r < spriteArr.length; r++) {
        let row = spriteArr[r];
        for (let c = 0; c < row.length; c++) {
            if (row[c] === '1') {
                ctx.fillRect(x + c * PIXEL_SIZE, y + r * PIXEL_SIZE, PIXEL_SIZE, PIXEL_SIZE);
            }
        }
    }
}

// Global Variables
let score = 0;
let lives = 3;
let gameState = 'waiting'; // playing, gameover, waiting
let animationId;
let playerName = obtenerNombreJugador();
const playerId = obtenerJugadorIdPersistente();
let canalComunicacion = null;
let posicionRankingActual = null;
let totalJugadoresConectados = 0;
let todosLosJugadoresMuertos = false;

function obtenerPrefijoApiSpaceInvaders() {
    const pathname = (window.location.pathname || '').replace(/\/+$/, '');
    if (pathname.startsWith('/server/space_invaders')) {
        return '/server/space_invaders';
    }

    return '';
}

// Game timing (rhythm)
let ticks = 0;
let waveNumber = 1;
let waveBaseSpeed = 35; // Más desafiante desde R1
let moveInterval = waveBaseSpeed;
let alienFireChance = 0.05; // Más disparos
let alienBulletSpeed = 7;
let currentFireChance = alienFireChance;
let currentBulletSpeed = alienBulletSpeed;
let alienDirection = 1;
let alienStepY = 0; // If stepped down this frame

// Entities
let player;
let aliens = [];
let alienBullets = [];
let playerBullet = null; // Original only allows 1 bullet on screen
let shields = [];
let explosions = [];

const keys = {
    ArrowLeft: false,
    ArrowRight: false,
    Space: false
};

class CanalComunicacionSpaceInvaders {
    constructor(traductor, conexion, jugadorId, nombreJugador, intervaloActualizacionMs) {
        this.traductor = traductor;
        this.conexion = conexion;
        this.jugadorId = jugadorId;
        this.nombreJugador = nombreJugador;
        this.intervaloActualizacionMs = intervaloActualizacionMs;
        this.timerActualizacion = null;
        this.muerteNotificada = false;
        this.conexionActiva = false;
        this.recepcionActiva = false;
    }

    reiniciarPartida() {
        this.detenerActualizacionPeriodica();
        this.muerteNotificada = false;
    }

    iniciarActualizacionPeriodicaPuntuacion(obtenerPuntuacionActual, estaPartidaActiva) {
        this.detenerActualizacionPeriodica();

        this.timerActualizacion = window.setInterval(() => {
            if (this.muerteNotificada || !estaPartidaActiva()) {
                return;
            }

            const puntuacionActual = obtenerPuntuacionActual();
            this.enviarActualizacionPuntuacion(puntuacionActual)
                .catch((error) => console.error('Error enviando puntuacion periodica:', error));
        }, this.intervaloActualizacionMs);
    }

    detenerActualizacionPeriodica() {
        if (this.timerActualizacion !== null) {
            clearInterval(this.timerActualizacion);
            this.timerActualizacion = null;
        }
    }

    async enviarActualizacionPuntuacion(puntuacionTotal) {
        if (this.muerteNotificada) {
            return;
        }

        const enviable = new ActualizarPuntuacionEnviable(
            this.jugadorId,
            this.nombreJugador,
            puntuacionTotal
        );

        await this.enviarEvento(enviable);
    }

    async notificarMuerte(puntuacionFinal) {
        if (this.muerteNotificada) {
            return;
        }

        this.muerteNotificada = true;
        this.detenerActualizacionPeriodica();

        const enviable = new NotificarMuerteJugadorEnviable(
            this.jugadorId,
            this.nombreJugador,
            puntuacionFinal
        );

        await this.enviarEvento(enviable);
    }

    async enviarEvento(enviable) {
        await this.asegurarConexionActiva();
        await this.traductor.enviar(enviable);
    }

    async asegurarConexionActiva() {
        if (!this.conexionActiva) {
            await this.conexion.conectar();
            this.conexionActiva = true;
            this.iniciarRecepcionContinua();
        }
    }

    iniciarRecepcionContinua() {
        if (this.recepcionActiva) {
            return;
        }

        this.recepcionActiva = true;
        this.bucleRecepcion();
    }

    async bucleRecepcion() {
        while (this.recepcionActiva) {
            try {
                await this.traductor.recibirYProcesar();
            } catch (error) {
                if (!this.recepcionActiva) {
                    break;
                }

                console.error('Error recibiendo actualizaciones del backend:', error);
                await this.esperar(300);
            }
        }
    }

    esperar(ms) {
        return new Promise((resolve) => window.setTimeout(resolve, ms));
    }
}

class EventoJugadorSpaceInvadersEnviable extends Enviable {
    constructor(comando, jugadorId, nombreJugador, puntuacion) {
        super();
        this.comando = comando;
        this.jugadorId = jugadorId;
        this.nombreJugador = nombreJugador;
        this.puntuacion = puntuacion;
    }

    out() {
        return JSON.stringify({
            comando: this.comando,
            jugadorId: this.jugadorId,
            nombreJugador: this.nombreJugador,
            puntuacion: this.puntuacion,
        });
    }

    in(entrada) {
        if (typeof entrada !== 'string') {
            throw new Error('EventoJugadorSpaceInvadersEnviable.in requiere un string JSON');
        }

        const data = JSON.parse(entrada);
        this.comando = data.comando ?? this.comando;
        this.jugadorId = data.jugadorId ?? this.jugadorId;
        this.nombreJugador = data.nombreJugador ?? this.nombreJugador;
        this.puntuacion = typeof data.puntuacion === 'number' ? data.puntuacion : this.puntuacion;
    }
}

class ActualizarPuntuacionEnviable extends EventoJugadorSpaceInvadersEnviable {
    constructor(jugadorId, nombreJugador, puntuacion) {
        super(COMANDO_ACTUALIZAR_PUNTUACION, jugadorId, nombreJugador, puntuacion);
    }
}

class NotificarMuerteJugadorEnviable extends EventoJugadorSpaceInvadersEnviable {
    constructor(jugadorId, nombreJugador, puntuacion) {
        super(COMANDO_NOTIFICAR_MUERTE, jugadorId, nombreJugador, puntuacion);
    }
}

class EstadoJugadoresActualizadoEvento {
    constructor(onEstadoActualizado) {
        this.onEstadoActualizado = onEstadoActualizado;
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

        if (!data || data.comando !== COMANDO_ESTADO_JUGADORES || !Array.isArray(data.jugadores)) {
            return;
        }

        this.onEstadoActualizado(data.jugadores);
    }
}

function normalizarJugadoresEstado(jugadores) {
    return jugadores
        .map((jugador) => ({
            jugadorId: typeof jugador.jugadorId === 'string' ? jugador.jugadorId : '',
            nombreJugador:
                typeof jugador.nombreJugador === 'string' ? jugador.nombreJugador
                    : (typeof jugador.player === 'string' ? jugador.player : ''),
            puntuacion:
                Number.isFinite(jugador.puntuacion) ? jugador.puntuacion
                    : (Number.isFinite(jugador.score) ? jugador.score : 0),
            muerto: typeof jugador.muerto === 'boolean' ? jugador.muerto : Boolean(jugador.dead),
        }))
        .filter((jugador) => jugador.jugadorId)
        .sort((a, b) => b.puntuacion - a.puntuacion);
}

function actualizarEstadoMultijugador(jugadores) {
    const jugadoresNormalizados = normalizarJugadoresEstado(jugadores);
    totalJugadoresConectados = jugadoresNormalizados.length;
    todosLosJugadoresMuertos =
        jugadoresNormalizados.length > 0 && jugadoresNormalizados.every((jugador) => jugador.muerto);

    const indiceJugador = jugadoresNormalizados.findIndex((jugador) => jugador.jugadorId === playerId);
    posicionRankingActual = indiceJugador >= 0 ? indiceJugador + 1 : null;

    if (rankText) {
        rankText.innerText = posicionRankingActual !== null
            ? `${posicionRankingActual}/${totalJugadoresConectados}`
            : '--';
    }

    if (playersText) {
        playersText.innerText = `${totalJugadoresConectados}`;
    }
}

function obtenerNombreJugador() {
    const params = new URLSearchParams(window.location.search);
    const nombreEnQuery = params.get('player');

    if (typeof nombreEnQuery === 'string' && nombreEnQuery.trim()) {
        return nombreEnQuery.trim();
    }

    return 'UNKNOWN';
}

function obtenerJugadorIdPersistente() {
    const claveStorage = 'space-invaders-player-id';

    try {
        const existente = localStorage.getItem(claveStorage);
        if (existente && existente.trim()) {
            return existente;
        }

        const nuevoId = generarUuid();
        localStorage.setItem(claveStorage, nuevoId);
        return nuevoId;
    } catch {
        return generarUuid();
    }
}

function generarUuid() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        return window.crypto.randomUUID();
    }

    // Fallback simple si randomUUID no esta disponible
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
        const random = Math.floor(Math.random() * 16);
        const value = char === 'x' ? random : (random & 0x3) | 0x8;
        return value.toString(16);
    });
}

document.addEventListener('keydown', (e) => {
    if (e.code === 'ArrowLeft') { keys.ArrowLeft = true; e.preventDefault(); }
    if (e.code === 'ArrowRight') { keys.ArrowRight = true; e.preventDefault(); }
    if (e.code === 'Space') { keys.Space = true; e.preventDefault(); }
});

document.addEventListener('keyup', (e) => {
    if (e.code === 'ArrowLeft') keys.ArrowLeft = false;
    if (e.code === 'ArrowRight') keys.ArrowRight = false;
    if (e.code === 'Space') keys.Space = false;
});

class Player {
    constructor() {
        this.width = SPRITE_PLAYER[0].length * PIXEL_SIZE;
        this.height = SPRITE_PLAYER.length * PIXEL_SIZE;
        this.x = 50;
        this.y = canvas.height - this.height - 10;
        this.speed = 3;
        this.cooldown = 0;
        this.deadTimer = 0;
    }

    draw() {
        if(this.deadTimer > 0) {
            // Blink if dead
            if (Math.floor(Date.now() / 150) % 2 === 0) {
                drawSprite(SPRITE_ALIEN_EXP, this.x, this.y, COLOR_PLAYER); 
            }
        } else {
            drawSprite(SPRITE_PLAYER, this.x, this.y, COLOR_PLAYER);
        }
    }

    update() {
        if (this.deadTimer > 0) {
            this.deadTimer--;
            if (this.deadTimer <= 0) {
                if (lives <= 0) {
                    gameOver();
                } else {
                    this.x = 50; // respawn
                }
            }
            return;
        }

        if (keys.ArrowLeft && this.x > 0) this.x -= this.speed;
        if (keys.ArrowRight && this.x + this.width < canvas.width) this.x += this.speed;

        if (keys.Space && !playerBullet && this.cooldown <= 0) {
            playerBullet = new Bullet(this.x + this.width / 2 - PIXEL_SIZE/2, this.y, -10, '#ffffff');
            this.cooldown = 10;
        }
        if (this.cooldown > 0) this.cooldown--;
    }
}

class Alien {
    constructor(x, y, type) {
        this.x = x;
        this.y = y;
        this.type = type; // 1, 2, 3
        this.frame = 0; // 0 = A, 1 = B
        
        let sprite = this.getSprite();
        this.width = sprite[0].length * PIXEL_SIZE;
        this.height = sprite.length * PIXEL_SIZE;
        this.points = type === 1 ? 30 : (type === 2 ? 20 : 10);
    }

    getSprite() {
        if (this.type === 1) return this.frame === 0 ? SPRITE_ALIEN1_A : SPRITE_ALIEN1_B;
        if (this.type === 2) return this.frame === 0 ? SPRITE_ALIEN2_A : SPRITE_ALIEN2_B;
        return this.frame === 0 ? SPRITE_ALIEN3_A : SPRITE_ALIEN3_B;
    }

    draw() {
        drawSprite(this.getSprite(), this.x, this.y, COLOR_ALIEN);
    }
}

class Bullet {
    constructor(x, y, speedY, color) {
        this.x = x;
        this.y = y;
        this.width = PIXEL_SIZE;
        this.height = PIXEL_SIZE * 3;
        this.speedY = speedY;
        this.color = color;
    }
    draw() {
        ctx.fillStyle = this.color;
        ctx.fillRect(this.x, this.y, this.width, this.height);
    }
    update() {
        this.y += this.speedY;
    }
}

class Shield {
    constructor(x, y) {
        this.x = x;
        this.y = y;
        this.blocks = [];
        this.width = SPRITE_SHIELD[0].length * PIXEL_SIZE;
        this.height = SPRITE_SHIELD.length * PIXEL_SIZE;

        // Parse sprite into individual blocks
        for (let r = 0; r < SPRITE_SHIELD.length; r++) {
            let row = SPRITE_SHIELD[r];
            for (let c = 0; c < row.length; c++) {
                if (row[c] === '1') {
                    this.blocks.push({
                        x: x + c * PIXEL_SIZE,
                        y: y + r * PIXEL_SIZE,
                        active: true
                    });
                }
            }
        }
    }

    draw() {
        ctx.fillStyle = COLOR_PLAYER;
        this.blocks.forEach(b => {
            if(b.active) ctx.fillRect(b.x, b.y, PIXEL_SIZE, PIXEL_SIZE);
        });
    }

    // Check pixel perfect collision
    checkCollision(rect) {
        let hit = false;
        let radius = PIXEL_SIZE * 2; // Damage radius
        for (let i = 0; i < this.blocks.length; i++) {
            let b = this.blocks[i];
            if (b.active && 
                rect.x < b.x + PIXEL_SIZE && rect.x + rect.width > b.x &&
                rect.y < b.y + PIXEL_SIZE && rect.y + rect.height > b.y) {
                
                // Explode nearby blocks
                this.blocks.forEach(otherB => {
                    if (otherB.active) {
                        let dx = otherB.x - rect.x;
                        let dy = otherB.y - rect.y;
                        if (Math.sqrt(dx*dx + dy*dy) < radius) {
                            otherB.active = false;
                        }
                    }
                });
                hit = true;
                break; // One bullet hit is enough
            }
        }
        return hit;
    }
}

class Explosion {
    constructor(x, y) {
        this.x = x;
        this.y = y;
        this.timer = 15; // Frames to display
    }
    draw() {
        drawSprite(SPRITE_ALIEN_EXP, this.x, this.y, COLOR_ALIEN);
    }
    update() {
        this.timer--;
    }
}

function initGame() {
    if(animationId) cancelAnimationFrame(animationId);

    if (!canalComunicacion) {
        const apiBase = obtenerPrefijoApiSpaceInvaders();
        const conexion = new FetchApiConexion(`${apiBase}/api/event`, `${apiBase}/api/updates`, {
            playerId,
            pollingIntervalMs: INTERVALO_POLLING_ESTADO_MS,
        });
        const envio = new JsonEnvio();
        const recibo = new JsonRecibo().conEvento(
            COMANDO_ESTADO_JUGADORES,
            new EstadoJugadoresActualizadoEvento(actualizarEstadoMultijugador)
        );
        const traductor = new Traductor(conexion, envio, recibo);

        canalComunicacion = new CanalComunicacionSpaceInvaders(
            traductor,
            conexion,
            playerId,
            playerName,
            INTERVALO_ACTUALIZACION_PUNTUACION_MS
        );
    }

    canalComunicacion.reiniciarPartida();
    actualizarEstadoMultijugador([]);
    
    player = new Player();
    aliens = [];
    alienBullets = [];
    playerBullet = null;
    shields = [];
    explosions = [];
    score = 0;
    lives = 3;
    waveNumber = 1;
    waveBaseSpeed = 35; 
    moveInterval = waveBaseSpeed;
    alienFireChance = 0.05; 
    alienBulletSpeed = 7;
    alienDirection = 1;
    gameState = 'playing';

    updateHUD();
    gameOverScreen.classList.add('hidden');

    spawnAliens();
    spawnShields();

    canalComunicacion.iniciarActualizacionPeriodicaPuntuacion(
        () => score,
        () => gameState === 'playing'
    );

    canalComunicacion.enviarActualizacionPuntuacion(score)
        .catch((err) => console.error('Error enviando puntuacion inicial:', err));

    gameLoop();
}

function spawnAliens() {
    aliens = [];
    const rows = 5;
    const cols = 11;
    const startX = 60;
    const startY = 80;
    const spacingX = 35;
    const spacingY = 30;

    for (let r = 0; r < rows; r++) {
        let type = 1; // Squid top
        if (r === 1 || r === 2) type = 2; // Crab middle
        if (r === 3 || r === 4) type = 3; // Octopus bottom

        for (let c = 0; c < cols; c++) {
            aliens.push(new Alien(startX + c * spacingX, startY + r * spacingY, type));
        }
    }
}

function spawnShields() {
    shields = [];
    const numShields = 4;
    const startX = 80;
    const shieldWidth = SPRITE_SHIELD[0].length * PIXEL_SIZE;
    const spacing = (canvas.width - 2 * startX - numShields * shieldWidth) / 3;
    const y = canvas.height - 100;

    for (let i = 0; i < numShields; i++) {
        shields.push(new Shield(startX + i * (shieldWidth + spacing), y));
    }
}

function padScore(num) {
    return num.toString().padStart(4, '0');
}

function updateHUD() {
    score1.innerText = padScore(score);
    livesText.innerText = lives;
    roundText.innerText = waveNumber;
    
    livesIcons.innerHTML = '';
    // Draw life icons minus the active player
    for(let i=0; i<lives-1; i++){
        let icon = document.createElement('div');
        icon.className = 'life-icon';
        livesIcons.appendChild(icon);
    }
}

function updateAliens() {
    // 1. Cálculo de parámetros dinámicos intra-ronda (MÁS SUAVE)
    if (aliens.length > 0) {
        let initialCount = 55;
        let countRatio = aliens.length / initialCount;
        let inverseRatio = 1 - countRatio; 
        
        // La velocidad escala de waveBaseSpeed a un 40% (antes era 20%, ahora es más lento al final)
        moveInterval = Math.max(1, Math.floor(waveBaseSpeed * (0.4 + 0.6 * countRatio)));
        
        // La agresividad escala menos agresivamente durante la horda
        currentFireChance = alienFireChance * (1 + inverseRatio * 0.8); 
        currentBulletSpeed = alienBulletSpeed * (1 + inverseRatio * 0.3);
    }

    // 2. Control del paso de tiempo y movimiento
    ticks++;
    if (ticks >= moveInterval) {
        ticks = 0;
        let hitEdge = false;
        
        // Comprobar bordes
        aliens.forEach(a => {
            if (alienDirection === 1 && a.x + a.width + 10 > canvas.width) hitEdge = true;
            if (alienDirection === -1 && a.x - 10 < 0) hitEdge = true;
        });

        if (hitEdge) {
            alienDirection *= -1;
            aliens.forEach(a => {
                a.y += 20;
                a.frame = (a.frame === 0 ? 1 : 0);
                if (a.y + a.height >= player.y) gameOver();
            });
        } else {
            let stepX = 10 * alienDirection;
            aliens.forEach(a => {
                a.x += stepX;
                a.frame = (a.frame === 0 ? 1 : 0);
            });
        }

        // 3. Disparo
        fireAlienBullet();
    }

    // 4. Manejo de cambio de horda (PROGRESIÓN MÁS GRADUAL)
    if (aliens.length === 0 && gameState === 'playing') {
        waveNumber++;
        
        // En lugar de heredar el máximo pico, subimos la dificultad base un 25% respecto al inicio anterior
        waveBaseSpeed = Math.max(5, Math.floor(waveBaseSpeed * 0.75)); 
        alienFireChance = Math.min(0.20, alienFireChance + 0.01);
        alienBulletSpeed = Math.min(12, alienBulletSpeed + 1);

        spawnAliens();
        spawnShields(); 
        updateHUD();
    }
}

function fireAlienBullet() {
    if (aliens.length > 0 && alienBullets.length < 3) {
        if (Math.random() < currentFireChance) {
            let shooter = aliens[Math.floor(Math.random() * aliens.length)];
            alienBullets.push(new Bullet(
                shooter.x + shooter.width/2 - PIXEL_SIZE/2, 
                shooter.y + shooter.height, 
                currentBulletSpeed, 
                '#ffffff'
            ));
        }
    }
}

function checkCollisions() {
    // Player Bullet vs Aliens
    if (playerBullet) {
        let hit = false;
        for (let i = aliens.length - 1; i >= 0; i--) {
            let a = aliens[i];
            if (playerBullet.x < a.x + a.width && playerBullet.x + playerBullet.width > a.x &&
                playerBullet.y < a.y + a.height && playerBullet.y + playerBullet.height > a.y) {
                
                explosions.push(new Explosion(a.x, a.y));
                score += a.points;
                updateHUD();
                aliens.splice(i, 1);
                playerBullet = null;
                hit = true;
                break;
            }
        }
        
        // Player Bullet vs Shields
        if (!hit && playerBullet) {
            for (let s of shields) {
                if (s.checkCollision(playerBullet)) {
                    playerBullet = null;
                    break;
                }
            }
        }

        if (playerBullet && playerBullet.y < 0) {
            playerBullet = null;
        }
    }

    // Alien Bullets vs Player & Shields
    for (let i = alienBullets.length - 1; i >= 0; i--) {
        let b = alienBullets[i];
        let hit = false;

        // Vs Player
        if (player.deadTimer <= 0 && 
            b.x < player.x + player.width && b.x + b.width > player.x &&
            b.y < player.y + player.height && b.y + b.height > player.y) {
            
            lives--;
            updateHUD();
            player.deadTimer = 60; // 1 second death animation
            alienBullets.splice(i, 1);
            continue;
        }

        // Vs Shields
        for (let s of shields) {
            if (s.checkCollision(b)) {
                alienBullets.splice(i, 1);
                hit = true;
                break;
            }
        }

        if (!hit && b.y > canvas.height) {
            alienBullets.splice(i, 1);
        }
    }
}

function gameOver() {
    if (gameState === 'gameover') {
        return;
    }

    gameState = 'gameover';
    finalScore.innerText = padScore(score);
    gameOverScreen.classList.remove('hidden');

    if (canalComunicacion) {
        canalComunicacion.notificarMuerte(score)
            .catch((err) => console.error('Error notificando muerte del jugador:', err))
            .finally(() => notificarFinPartidaAlContenedor());
    } else {
        notificarFinPartidaAlContenedor();
    }
}

function notificarFinPartidaAlContenedor() {
    if (!window.parent || window.parent === window) {
        return;
    }

    window.parent.postMessage(
        {
            type: 'SPACE_INVADERS_GAME_OVER',
            jugadorId: playerId,
            nombreJugador: playerName,
            puntuacionFinal: score,
        },
        window.location.origin
    );
}

function gameLoop() {
    if (gameState !== 'playing') return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    player.update();
    player.draw();

    updateAliens();
    aliens.forEach(a => a.draw());
    
    shields.forEach(s => s.draw());

    if (playerBullet) {
        playerBullet.update();
        playerBullet.draw();
    }

    alienBullets.forEach(b => {
        b.update();
        b.draw();
    });

    for (let i = explosions.length - 1; i >= 0; i--) {
        let exp = explosions[i];
        exp.update();
        exp.draw();
        if (exp.timer <= 0) explosions.splice(i, 1);
    }

    checkCollisions();

    animationId = requestAnimationFrame(gameLoop);
}

restartBtn.addEventListener('click', initGame);

// Instead of auto-starting, we initialize and run
initGame();

