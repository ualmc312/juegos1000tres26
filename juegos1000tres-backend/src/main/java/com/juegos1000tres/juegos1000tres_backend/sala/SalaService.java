package com.juegos1000tres.juegos1000tres_backend.sala;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.juegos1000tres.juegos1000tres_backend.juegos.AdivinaElPersonaje.AdivinaElPersonajeManager;
import com.juegos1000tres.juegos1000tres_backend.juegos.Dibujo.DibujoManager;
import com.juegos1000tres.juegos1000tres_backend.juegos.HablameDeTi.HablameDeTiManager;
import com.juegos1000tres.juegos1000tres_backend.juegos.PruebaWebSocket.PruebaWebSocketManager;
import com.juegos1000tres.juegos1000tres_backend.juegos.handicap.HandicapService;
import com.juegos1000tres.juegos1000tres_backend.modelos.Jugador;
import com.juegos1000tres.juegos1000tres_backend.modelos.Pantalla;
import com.juegos1000tres.juegos1000tres_backend.modelos.Sala;
import com.juegos1000tres.juegos1000tres_backend.sala.p2p.P2PSenalizacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SalaService {

    private static final Logger LOG = LoggerFactory.getLogger(SalaService.class);

    private final Map<String, SalaRoom> salas = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final JuegoManager juegoManager;
    private final PruebaWebSocketManager pruebaWebSocketManager;
    private final AdivinaElPersonajeManager adivinaElPersonajeManager;
    private final HablameDeTiManager hablameDeTiManager;
    private final DibujoManager dibujoManager;
    private final P2PSenalizacionService p2pSenalizacionService;
    private final ObjectProvider<HandicapService> handicapServiceProvider;
    private final SalaPersistenciaService salaPersistenciaService;

    public SalaService(
            JuegoManager juegoManager,
            PruebaWebSocketManager pruebaWebSocketManager,
            AdivinaElPersonajeManager adivinaElPersonajeManager,
            HablameDeTiManager hablameDeTiManager,
            DibujoManager dibujoManager,
            P2PSenalizacionService p2pSenalizacionService,
            ObjectProvider<HandicapService> handicapServiceProvider,
            SalaPersistenciaService salaPersistenciaService) {
        this.juegoManager = juegoManager;
        this.pruebaWebSocketManager = pruebaWebSocketManager;
        this.adivinaElPersonajeManager = adivinaElPersonajeManager;
        this.hablameDeTiManager = hablameDeTiManager;
        this.dibujoManager = dibujoManager;
        this.p2pSenalizacionService = p2pSenalizacionService;
        this.handicapServiceProvider = handicapServiceProvider;
        this.salaPersistenciaService = salaPersistenciaService;
    }

    public SalaRespuesta crearSala(String nombre, String usuarioId, boolean esInvitado) {
        String uuid = generarIdUnico();
        String nombreFinal = (nombre == null || nombre.isBlank()) ? "Host" : nombre.trim();

        if (esInvitado) {
            nombreFinal = "invitado";
        }

        Jugador host = new Jugador(nombreFinal, esInvitado ? null : usuarioId);
        Sala sala = new Sala(host, new Pantalla("Lobby"));
        SalaRoom room = new SalaRoom(uuid, sala, host.getId().toString(), usuarioId);

        if (esInvitado) {
            room.registrarInvitado(usuarioId);
        }

        salas.put(uuid, room);

        try {
            String hostPersistente = (usuarioId == null || usuarioId.isBlank())
                    ? host.getId().toString()
                    : usuarioId.trim();
            this.salaPersistenciaService.registrarSalaCreada(uuid, host.getNombre(), hostPersistente);
        } catch (RuntimeException ex) {
            // La persistencia no debe bloquear la sala en memoria.
        }

        return construirRespuesta(room, host.getId().toString());
    }

    public SalaRespuesta unirse(String uuid, String nombre, String usuarioId, boolean esInvitado) {
        SalaRoom room = obtenerSala(uuid);
        Jugador jugador = room.agregarJugador(nombre, usuarioId, esInvitado);

        return construirRespuesta(room, jugador.getId().toString());
    }

    public SalaRespuesta estado(String uuid) {
        SalaRoom room = obtenerSala(uuid);

        return construirRespuesta(room, null);
    }

    public SalaRespuesta cambiarPantalla(String uuid, String actorId, String jugadorId) {
        SalaRoom room = obtenerSala(uuid);
        room.cambiarPantalla(actorId, jugadorId);

        return construirRespuesta(room, null);
    }

    public SalaRespuesta cambiarJuego(String uuid, String actorId, String juego) {
        SalaRoom room = obtenerSala(uuid);
        String juegoAnterior = room.getJuegoActual();
        room.cambiarJuego(actorId, juego);

        try {
            if (juegoAnterior != null && !juegoAnterior.isBlank()) {
                this.salaPersistenciaService.registrarResultadosJuego(uuid, room.getJugadores());
            }

            this.salaPersistenciaService.registrarJuegoIniciado(uuid, juego, room.getJugadores());

            if ("prueba-websocket".equalsIgnoreCase(juego)) {
                this.pruebaWebSocketManager.crearInstanciaParaSala(uuid);
            } else if ("adivina-el-personaje".equalsIgnoreCase(juego)) {
                this.adivinaElPersonajeManager.crearInstanciaParaSala(uuid);
            } else if ("hablame-de-ti".equalsIgnoreCase(juego)) {
                this.hablameDeTiManager.crearInstanciaParaSala(uuid);
            } else if ("dibujo".equalsIgnoreCase(juego)) {
                this.dibujoManager.crearInstanciaParaSala(uuid);
            } else {
                this.juegoManager.crearInstanciaJuego(uuid, juego);
            }
        } catch (RuntimeException ex) {
            // no bloquear la respuesta por errores internos del manager
        }

        return construirRespuesta(room, null);
    }

    public void finalizarJuego(String uuid, String actorId) {
        SalaRoom room = obtenerSala(uuid);
        String juegoAntes = room.getJuegoActual();
        room.finalizarJuego(actorId);

        try {
            if (juegoAntes != null && !juegoAntes.isBlank() && !room.resultadosPersistidos()) {
                this.salaPersistenciaService.registrarResultadosJuego(uuid, room.getJugadores());
                room.marcarResultadosPersistidos();
            }
        } catch (RuntimeException ex) {
            // no bloquear la finalizacion por un fallo de persistencia
        }

        try {
            if ("prueba-websocket".equalsIgnoreCase(juegoAntes)) {
                this.pruebaWebSocketManager.detenerInstanciaParaSala(uuid);
            } else if ("adivina-el-personaje".equalsIgnoreCase(juegoAntes)) {
                this.adivinaElPersonajeManager.detenerInstanciaParaSala(uuid);
            } else if ("hablame-de-ti".equalsIgnoreCase(juegoAntes)) {
                this.hablameDeTiManager.detenerInstanciaParaSala(uuid);
            } else if ("dibujo".equalsIgnoreCase(juegoAntes)) {
                this.dibujoManager.detenerInstanciaParaSala(uuid);
            } else {
                this.juegoManager.detenerInstancia(uuid, juegoAntes);
            }
        } catch (RuntimeException ex) {
            // ignore
        }

        limpiarEstadoJuegoEspecial(uuid, juegoAntes);
    }

    /**
     * Expose registrarResultadosJuego to allow game instances to trigger
     * persistence of the current players' puntuaciones into historial.
     */
    public void registrarResultadosJuego(String uuid) {
        SalaRoom room = obtenerSala(uuid);
        try {
            if (!room.resultadosPersistidos()) {
                this.salaPersistenciaService.registrarResultadosJuego(uuid, room.getJugadores());
                room.marcarResultadosPersistidos();
            }
        } catch (RuntimeException ex) {
            // don't block game flow on persistence errors
        }
    }

    public void incrementarVictoria(String uuid, String jugadorId) {
        SalaRoom room = obtenerSala(uuid);
        LOG.info("Incrementar victoria request: sala={}, jugadorId={}", uuid, jugadorId);
        try {
            room.sumarVictoria(jugadorId);
            LOG.info("Victoria incrementada: sala={}, jugadorId={}", uuid, jugadorId);
        } catch (RuntimeException ex) {
            LOG.warn("Fallo al incrementar victoria: sala={}, jugadorId={}, motivo={}", uuid, jugadorId, ex.getMessage());
            throw ex;
        }
    }

    public void incrementarPuntuacion(String uuid, String jugadorId, int puntos) {
        if (puntos == 0) {
            return;
        }

        SalaRoom room = obtenerSala(uuid);
        room.sumarPuntos(jugadorId, puntos);
    }

    public void establecerPuntuacion(String uuid, String jugadorId, int puntuacion) {
        SalaRoom room = obtenerSala(uuid);
        room.establecerPuntuacion(jugadorId, puntuacion);
    }

    public String normalizarJugadorId(String uuid, String jugadorId, String nombreJugador) {
        SalaRoom room = obtenerSala(uuid);
        return room.resolverJugadorIdCanonical(jugadorId, nombreJugador);
    }

    public SalaRoom obtenerSalaRoom(String uuid) {
        return obtenerSala(uuid);
    }

    public String obtenerSalaActivaDeUsuario(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            return null;
        }

        return salas.values().stream()
                .filter(room -> usuarioId.equals(room.getCreadorUsuarioId()))
                .map(SalaRoom::getUuid)
                .findFirst()
                .orElse(null);
    }

    public void salir(String uuid, String jugadorId) {
        SalaRoom room = obtenerSala(uuid);
        String juegoAntes = room.getJuegoActual();

        if (room.esCreador(jugadorId)) {
            try {
                if (juegoAntes != null && !juegoAntes.isBlank() && !room.resultadosPersistidos()) {
                    this.salaPersistenciaService.registrarResultadosJuego(uuid, room.getJugadores());
                    room.marcarResultadosPersistidos();
                }
            } catch (RuntimeException ex) {
                // ignore
            }

            salas.remove(uuid);
            this.p2pSenalizacionService.limpiarSala(uuid);
            detenerJuegoActivoSiCorresponde(uuid, juegoAntes);
            return;
        }

        room.eliminarJugador(jugadorId);

        if (!room.isAbierta()) {
            try {
                if (juegoAntes != null && !juegoAntes.isBlank() && !room.resultadosPersistidos()) {
                    this.salaPersistenciaService.registrarResultadosJuego(uuid, room.getJugadores());
                    room.marcarResultadosPersistidos();
                }
            } catch (RuntimeException ex) {
                // ignore
            }

            salas.remove(uuid);
            detenerJuegoActivoSiCorresponde(uuid, juegoAntes);
        }
    }

    public void apagar(String uuid) {
        SalaRoom room = salas.get(uuid);
        String juegoAntes = room != null ? room.getJuegoActual() : null;

        if (room != null && juegoAntes != null && !juegoAntes.isBlank()) {
            try {
                if (!room.resultadosPersistidos()) {
                    this.salaPersistenciaService.registrarResultadosJuego(uuid, room.getJugadores());
                    room.marcarResultadosPersistidos();
                }
            } catch (RuntimeException ex) {
                // ignore
            }
        }

        salas.remove(uuid);
        this.p2pSenalizacionService.limpiarSala(uuid);
        detenerJuegoActivoSiCorresponde(uuid, juegoAntes);
    }

    private SalaRespuesta construirRespuesta(SalaRoom room, String jugadorId) {
        List<JugadorRespuesta> jugadores = room.getJugadores().stream()
            .map(jugador -> new JugadorRespuesta(
                jugador.getId().toString(),
                jugador.getNombre(),
                jugador.getVictorias()
            ))
                .toList();

        return new SalaRespuesta(
                room.getUuid(),
                jugadores,
                room.getHostId(),
                room.getPantallaId(),
            room.getJuegoActual(),
            room.getP2pHostPeerId(),
            jugadorId
        );
    }

    private SalaRoom obtenerSala(String uuid) {
        SalaRoom room = salas.get(uuid);

        if (room == null) {
            throw new SalaNoEncontradaException();
        }

        return room;
    }

    private String generarIdUnico() {
        for (int intentos = 0; intentos < 20; intentos++) {
            String candidato = formatearId(100000 + random.nextInt(900000));

            if (!salas.containsKey(candidato)) {
                return candidato;
            }
        }

        String candidato = formatearId((int) (System.currentTimeMillis() % 1_000_000));

        if (!salas.containsKey(candidato)) {
            return candidato;
        }

        return formatearId((int) (System.nanoTime() % 1_000_000));
    }

    private String formatearId(int valor) {
        return String.format("%06d", Math.abs(valor));
    }

    private void detenerJuegoActivoSiCorresponde(String uuid, String juego) {
        try {
            if ("prueba-websocket".equalsIgnoreCase(juego)) {
                this.pruebaWebSocketManager.detenerInstanciaParaSala(uuid);
            } else if ("adivina-el-personaje".equalsIgnoreCase(juego)) {
                this.adivinaElPersonajeManager.detenerInstanciaParaSala(uuid);
            } else if ("hablame-de-ti".equalsIgnoreCase(juego)) {
                this.hablameDeTiManager.detenerInstanciaParaSala(uuid);
            } else if (juego != null && !juego.isBlank()) {
                this.juegoManager.detenerInstancia(uuid, juego);
            }
        } catch (RuntimeException ex) {
            // ignore
        }

        limpiarEstadoJuegoEspecial(uuid, juego);
    }

    private void limpiarEstadoJuegoEspecial(String uuid, String juego) {
        if ("handicap".equalsIgnoreCase(juego)) {
            HandicapService handicapService = this.handicapServiceProvider.getIfAvailable();
            if (handicapService != null) {
                handicapService.limpiarPartida(uuid);
            }
        }
    }
}
