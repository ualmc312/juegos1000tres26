package com.juegos1000tres.juegos1000tres_backend.juegos.SpaceInvaders;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.ContextoEvento;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Evento;

public class ActualizarPuntuacionEvento implements Evento<String> {

    private static final Pattern PATRON_JUGADOR_ID = Pattern.compile("\\\"jugadorId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern PATRON_NOMBRE_JUGADOR = Pattern
        .compile("\\\"nombreJugador\\\"\\s*:\\s*\\\"((?:\\\\\\\\.|[^\\\"])*)\\\"");
    private static final Pattern PATRON_PUNTUACION = Pattern
        .compile("\\\"(?:puntuacion|puntuacionTotal)\\\"\\s*:\\s*(-?\\d+)");

    private final SpaceInvader juego;

    public ActualizarPuntuacionEvento(SpaceInvader juego) {
        this.juego = Objects.requireNonNull(juego, "El juego es obligatorio");
    }

    @Override
    public void hacer(String payload, ContextoEvento contexto) {
        Objects.requireNonNull(contexto, "El contexto de evento es obligatorio");

        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("El payload de actualizar puntuacion no puede estar vacio");
        }

        UUID jugadorId = extraerJugadorId(payload);
        String nombreJugador = extraerNombreJugador(payload);
        int puntuacionTotal = extraerPuntuacionTotal(payload);

        ejecutarConDatos(jugadorId, nombreJugador, puntuacionTotal, this.juego, contexto);
    }

    public void ejecutarConDatos(
            UUID jugadorId,
            String nombreJugador,
            int puntuacionTotal,
            SpaceInvader juegoModelo,
            ContextoEvento contexto) {
        Objects.requireNonNull(juegoModelo, "El modelo de juego es obligatorio");
        Objects.requireNonNull(contexto, "El contexto de evento es obligatorio");

        juegoModelo.registrarJugador(jugadorId, nombreJugador);
        juegoModelo.actualizarPuntuacion(jugadorId, puntuacionTotal);
        contexto.enviar(juegoModelo.crearEstadoEnviable());
    }

    public SpaceInvader getJuego() {
        return juego;
    }

    private UUID extraerJugadorId(String payload) {
        Matcher matcher = PATRON_JUGADOR_ID.matcher(payload);
        if (!matcher.find()) {
            throw new IllegalArgumentException("El payload no contiene jugadorId para actualizar puntuacion");
        }

        return UUID.fromString(matcher.group(1));
    }

    private String extraerNombreJugador(String payload) {
        Matcher matcher = PATRON_NOMBRE_JUGADOR.matcher(payload);
        if (!matcher.find()) {
            throw new IllegalArgumentException("El payload no contiene nombreJugador para actualizar puntuacion");
        }

        return unescapeJson(matcher.group(1));
    }

    private int extraerPuntuacionTotal(String payload) {
        Matcher matcher = PATRON_PUNTUACION.matcher(payload);
        if (!matcher.find()) {
            throw new IllegalArgumentException("El payload no contiene puntuacion para actualizar puntuacion");
        }

        return Integer.parseInt(matcher.group(1));
    }

    private String unescapeJson(String valor) {
        return valor.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
