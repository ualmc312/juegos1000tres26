package com.juegos1000tres.juegos1000tres_backend.sala;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.juegos1000tres.juegos1000tres_backend.juegos.AdivinaElPersonaje.AdivinaElPersonajeManager;
import com.juegos1000tres.juegos1000tres_backend.juegos.Dibujo.DibujoManager;
import com.juegos1000tres.juegos1000tres_backend.juegos.HablameDeTi.HablameDeTiManager;
import com.juegos1000tres.juegos1000tres_backend.juegos.PruebaWebSocket.PruebaWebSocketManager;
import com.juegos1000tres.juegos1000tres_backend.modelos.Jugador;
import com.juegos1000tres.juegos1000tres_backend.modelos.Pantalla;
import com.juegos1000tres.juegos1000tres_backend.modelos.Sala;
import com.juegos1000tres.juegos1000tres_backend.sala.p2p.P2PSenalizacionService;

@Service
public class SalaService {

    private final Map<String, SalaRoom> salas = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final JuegoManager juegoManager;
    private final PruebaWebSocketManager pruebaWebSocketManager;
    private final AdivinaElPersonajeManager adivinaElPersonajeManager;
    private final HablameDeTiManager hablameDeTiManager;
    private final DibujoManager dibujoManager;
    private final P2PSenalizacionService p2pSenalizacionService;

    public SalaService(
            JuegoManager juegoManager,
            PruebaWebSocketManager pruebaWebSocketManager,
            AdivinaElPersonajeManager adivinaElPersonajeManager,
            HablameDeTiManager hablameDeTiManager,
            DibujoManager dibujoManager,
            P2PSenalizacionService p2pSenalizacionService) {
        this.juegoManager = juegoManager;
        this.pruebaWebSocketManager = pruebaWebSocketManager;
        this.adivinaElPersonajeManager = adivinaElPersonajeManager;
        this.hablameDeTiManager = hablameDeTiManager;
        this.dibujoManager = dibujoManager;
        this.p2pSenalizacionService = p2pSenalizacionService;
    }

    public SalaRespuesta crearSala(String nombre, String usuarioId, boolean esInvitado) {
        String uuid = generarIdUnico();
        String nombreFinal = (nombre == null || nombre.isBlank()) ? "Host" : nombre.trim();

        if (esInvitado) {
            nombreFinal = "invitado";
        }

        Jugador host = new Jugador(nombreFinal);
        Sala sala = new Sala(host, new Pantalla("Lobby"));
        SalaRoom room = new SalaRoom(uuid, sala, host.getId().toString());

        if (esInvitado) {
            room.registrarInvitado(usuarioId);
        }

        salas.put(uuid, room);

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
        room.cambiarJuego(actorId, juego);

        try {
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
    }

    public void incrementarVictoria(String uuid, String jugadorId) {
        SalaRoom room = obtenerSala(uuid);
        room.sumarVictoria(jugadorId);
    }

    public SalaRoom obtenerSalaRoom(String uuid) {
        return obtenerSala(uuid);
    }

    public void salir(String uuid, String jugadorId) {
        SalaRoom room = obtenerSala(uuid);
        String juegoAntes = room.getJuegoActual();

        if (room.esCreador(jugadorId)) {
            salas.remove(uuid);
            this.p2pSenalizacionService.limpiarSala(uuid);
            detenerJuegoActivoSiCorresponde(uuid, juegoAntes);
            return;
        }

        room.eliminarJugador(jugadorId);

        if (!room.isAbierta()) {
            salas.remove(uuid);
            detenerJuegoActivoSiCorresponde(uuid, juegoAntes);
        }
    }

    public void apagar(String uuid) {
        String juegoAntes = salas.containsKey(uuid) ? salas.get(uuid).getJuegoActual() : null;
        salas.remove(uuid);
        this.p2pSenalizacionService.limpiarSala(uuid);
        detenerJuegoActivoSiCorresponde(uuid, juegoAntes);
    }

    private SalaRespuesta construirRespuesta(SalaRoom room, String jugadorId) {
        List<JugadorRespuesta> jugadores = room.getJugadores().stream()
            .map(jugador -> new JugadorRespuesta(
                jugador.getId().toString(),
                jugador.getNombre(),
                jugador.getPuntuacion()
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
    }
}
