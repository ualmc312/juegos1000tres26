package com.juegos1000tres.juegos1000tres_backend.juegos.SpaceInvaders.pruebas;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Envio;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Recibo;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.juegos.SpaceInvaders.EstadoJugadoresSpaceInvaders;
import com.juegos1000tres.juegos1000tres_backend.juegos.SpaceInvaders.SpaceInvader;
import com.juegos1000tres.juegos1000tres_backend.juegos.SpaceInvaders.pruebas.comunicacion.ConexionNulaPruebas;
import com.juegos1000tres.juegos1000tres_backend.juegos.SpaceInvaders.pruebas.comunicacion.ConexionPantallaPruebas;

@Service
public class SpaceInvadersPruebaService {

    private static final String COMANDO_ESTADO_JUGADORES = "ESTADO_JUGADORES";
    private static final int MAX_UPDATES_PER_PLAYER = 50;

    private final SpaceInvader juego;
    private final ConexionPantallaPruebas conexionPantalla;
    private final Traductor<String> traductorEventos;
    private final Map<String, EstadoJugador> scoreboard;
    private final Map<String, Deque<Map<String, Object>>> updatesByPlayer;

    public SpaceInvadersPruebaService() {
        Traductor<String> traductorNulo = new Traductor<>(
                new ConexionNulaPruebas("space-invaders-pruebas"),
                Envio.paraStringDesdeOut(),
                Recibo.paraJsonString());

        this.conexionPantalla = new ConexionPantallaPruebas("space-invaders-pruebas");
        Traductor<String> traductorPantalla = new Traductor<>(
            this.conexionPantalla,
            Envio.paraStringDesdeOut(),
            Recibo.paraJsonString());

        this.juego = new SpaceInvader(4, traductorNulo, traductorPantalla);
        Recibo<String> reciboJuego = this.juego.registrarEventosEnRecibo(Recibo.paraJsonString());

        this.traductorEventos = new Traductor<>(
                new ConexionNulaPruebas("space-invaders-pruebas"),
                Envio.paraStringDesdeOut(),
                reciboJuego);

        this.scoreboard = new LinkedHashMap<>();
        this.updatesByPlayer = new LinkedHashMap<>();
    }

    public synchronized Map<String, Object> procesarEvento(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("Invalid JSON");
        }

        String payload = construirPayloadEvento(data);
        Optional<String> respuesta = this.traductorEventos.procesar(payload);

        respuesta.ifPresent(this::actualizarEstadoYPublicar);

        String comando = leerTextoObligatorio(data, "comando").toUpperCase(Locale.ROOT);
        String jugadorId = leerTextoObligatorio(data, "jugadorId");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("comando", comando);
        response.put("jugadorId", jugadorId);

        EstadoJugador estado = this.scoreboard.get(jugadorId);
        if (estado != null) {
            response.put("playerId", jugadorId);
            response.put("player", estado.player);
            response.put("score", estado.score);
            response.put("dead", estado.dead);
        }

        return response;
    }

    public synchronized List<Map<String, Object>> obtenerScoresOrdenados() {
        List<Map.Entry<String, EstadoJugador>> entries = new ArrayList<>(this.scoreboard.entrySet());
        entries.sort(
                Comparator.<Map.Entry<String, EstadoJugador>>comparingInt((entry) -> entry.getValue().score)
                        .reversed()
                        .thenComparing((entry) -> entry.getValue().player));

        List<Map<String, Object>> scores = new ArrayList<>();
        for (Map.Entry<String, EstadoJugador> entry : entries) {
            String jugadorId = entry.getKey();
            EstadoJugador estado = entry.getValue();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jugadorId", jugadorId);
            item.put("playerId", jugadorId);
            item.put("player", estado.player);
            item.put("score", estado.score);
            item.put("dead", estado.dead);
            scores.add(item);
        }

        return scores;
    }

    public synchronized Optional<Map<String, Object>> obtenerSiguienteActualizacion(String playerId) {
        String jugadorId = normalizarPlayerId(playerId);
        Deque<Map<String, Object>> cola = this.updatesByPlayer.computeIfAbsent(jugadorId, (_id) -> new ArrayDeque<>());
        return Optional.ofNullable(cola.pollFirst());
    }

    public synchronized Optional<Map<String, Object>> obtenerSiguienteActualizacionPantalla(String screenId) {
        Optional<String> payload = this.conexionPantalla.obtenerSiguienteActualizacion(screenId);
        return payload.map(this::convertirPayloadPantallaAMapa);
    }

    private void actualizarEstadoYPublicar(String payloadEstado) {
        EstadoJugadoresSpaceInvaders estado = new EstadoJugadoresSpaceInvaders();
        estado.in(payloadEstado);

        List<EstadoJugadorDTOInterno> jugadores = new ArrayList<>();
        for (EstadoJugadoresSpaceInvaders.EstadoJugadorDTO jugador : estado.getJugadores()) {
            if (jugador.getJugadorId() == null) {
                continue;
            }

            String jugadorId = jugador.getJugadorId().toString();
            String nombreJugador = (jugador.getNombreJugador() == null || jugador.getNombreJugador().isBlank())
                    ? "Jugador-" + jugadorId.substring(0, Math.min(8, jugadorId.length()))
                    : jugador.getNombreJugador().trim();

            jugadores.add(new EstadoJugadorDTOInterno(
                    jugadorId,
                    nombreJugador,
                    jugador.getPuntuacion(),
                    jugador.isMuerto()));
        }

        for (EstadoJugadorDTOInterno jugador : jugadores) {
            this.scoreboard.put(jugador.jugadorId,
                    new EstadoJugador(jugador.nombreJugador, jugador.puntuacion, jugador.muerto));
        }

        this.juego.publicarEstadoEnPantalla();

        publicarEstadoJugadores();
    }

    private Map<String, Object> convertirPayloadPantallaAMapa(String payloadEstado) {
        EstadoJugadoresSpaceInvaders estado = new EstadoJugadoresSpaceInvaders();
        estado.in(payloadEstado);

        List<Map<String, Object>> jugadores = new ArrayList<>();
        for (EstadoJugadoresSpaceInvaders.EstadoJugadorDTO jugador : estado.getJugadores()) {
            if (jugador.getJugadorId() == null) {
                continue;
            }

            String jugadorId = jugador.getJugadorId().toString();
            String nombreJugador = (jugador.getNombreJugador() == null || jugador.getNombreJugador().isBlank())
                    ? "Jugador-" + jugadorId.substring(0, Math.min(8, jugadorId.length()))
                    : jugador.getNombreJugador().trim();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jugadorId", jugadorId);
            item.put("playerId", jugadorId);
            item.put("player", nombreJugador);
            item.put("score", jugador.getPuntuacion());
            item.put("dead", jugador.isMuerto());
            jugadores.add(item);
        }

        jugadores.sort(
                Comparator.<Map<String, Object>>comparingInt((item) -> (Integer) item.get("score"))
                        .reversed()
                        .thenComparing((item) -> (String) item.get("player")));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comando", COMANDO_ESTADO_JUGADORES);
        payload.put("jugadores", jugadores);
        return payload;
    }

    private void publicarEstadoJugadores() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comando", COMANDO_ESTADO_JUGADORES);
        payload.put("jugadores", obtenerScoresOrdenados());

        for (String jugadorId : this.scoreboard.keySet()) {
            Deque<Map<String, Object>> cola = this.updatesByPlayer.computeIfAbsent(jugadorId, (_id) -> new ArrayDeque<>());
            while (cola.size() >= MAX_UPDATES_PER_PLAYER) {
                cola.pollFirst();
            }
            cola.addLast(clonarPayload(payload));
        }
    }

    private Map<String, Object> clonarPayload(Map<String, Object> payload) {
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put("comando", payload.get("comando"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> jugadores = (List<Map<String, Object>>) payload.get("jugadores");
        List<Map<String, Object>> jugadoresCopy = new ArrayList<>();
        if (jugadores != null) {
            for (Map<String, Object> jugador : jugadores) {
                jugadoresCopy.add(new LinkedHashMap<>(jugador));
            }
        }

        copy.put("jugadores", jugadoresCopy);
        return copy;
    }

    private String construirPayloadEvento(Map<String, Object> data) {
        String comando = leerTextoObligatorio(data, "comando");
        String jugadorId = leerTextoObligatorio(data, "jugadorId");
        String nombreJugador = Optional.ofNullable(leerTextoOpcional(data, "nombreJugador")).orElse("");
        Integer puntuacion = leerEnteroOpcional(data.get("puntuacion"));

        StringBuilder json = new StringBuilder();
        json.append("{\"comando\":\"")
                .append(escapeJson(comando))
                .append("\",\"jugadorId\":\"")
                .append(escapeJson(jugadorId))
                .append("\",\"nombreJugador\":\"")
                .append(escapeJson(nombreJugador))
                .append("\"");

        if (puntuacion != null) {
            json.append(",\"puntuacion\":").append(puntuacion.intValue());
        }

        json.append("}");
        return json.toString();
    }

    private String leerTextoObligatorio(Map<String, Object> data, String campo) {
        Object value = data.get(campo);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing '" + campo + "'");
        }

        return text.trim();
    }

    private String leerTextoOpcional(Map<String, Object> data, String campo) {
        Object value = data.get(campo);
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }

        return text.trim();
    }

    private Integer leerEnteroOpcional(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("El campo 'puntuacion' debe ser numerico");
            }
        }

        throw new IllegalArgumentException("El campo 'puntuacion' debe ser numerico");
    }

    private String normalizarPlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("Missing query param 'playerId'");
        }

        return playerId.trim();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class EstadoJugador {
        private final String player;
        private final int score;
        private final boolean dead;

        private EstadoJugador(String player, int score, boolean dead) {
            this.player = player;
            this.score = score;
            this.dead = dead;
        }
    }

    private record EstadoJugadorDTOInterno(String jugadorId, String nombreJugador, int puntuacion, boolean muerto) {
    }
}
