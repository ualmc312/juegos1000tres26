package com.juegos1000tres.juegos1000tres_backend.juegos.SpaceInvaders;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.ContextoEvento;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Evento;

public class NotificarMuerteJugadorEvento implements Evento<String> {

    private static final Pattern PATRON_JUGADOR_ID = Pattern.compile("\\\"jugadorId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final SpaceInvader juego;

    public NotificarMuerteJugadorEvento(SpaceInvader juego) {
        this.juego = Objects.requireNonNull(juego, "El juego es obligatorio");
    }

    @Override
    public void hacer(String payload, ContextoEvento contexto) {
        Objects.requireNonNull(contexto, "El contexto de evento es obligatorio");

        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("El payload de notificar muerte no puede estar vacio");
        }

        UUID jugadorId = extraerJugadorId(payload);
        ejecutarConDatos(jugadorId, this.juego, contexto);
    }

    public void ejecutarConDatos(UUID jugadorId, SpaceInvader juegoModelo, ContextoEvento contexto) {
        Objects.requireNonNull(juegoModelo, "El modelo de juego es obligatorio");
        Objects.requireNonNull(contexto, "El contexto de evento es obligatorio");

        juegoModelo.marcarJugadorComoMuerto(jugadorId);
        contexto.enviar(juegoModelo.crearEstadoEnviable());
    }

    public SpaceInvader getJuego() {
        return juego;
    }

    private UUID extraerJugadorId(String payload) {
        Matcher matcher = PATRON_JUGADOR_ID.matcher(payload);
        if (!matcher.find()) {
            throw new IllegalArgumentException("El payload no contiene jugadorId para notificar muerte");
        }

        return UUID.fromString(matcher.group(1));
    }
}
