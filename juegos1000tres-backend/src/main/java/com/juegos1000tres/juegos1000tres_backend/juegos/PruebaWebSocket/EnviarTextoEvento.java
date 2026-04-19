package com.juegos1000tres.juegos1000tres_backend.juegos.PruebaWebSocket;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.ContextoEvento;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Evento;

public final class EnviarTextoEvento implements Evento<String> {

    private static final Pattern PATRON_JUGADOR_ID = Pattern.compile("\\\"jugadorId\\\"\\s*:\\s*\\\"((?:\\\\\\.|[^\\\"])*)\\\"");
    private static final Pattern PATRON_NOMBRE_JUGADOR = Pattern
            .compile("\\\"nombreJugador\\\"\\s*:\\s*\\\"((?:\\\\\\.|[^\\\"])*)\\\"");
    private static final Pattern PATRON_TEXTO = Pattern.compile("\\\"texto\\\"\\s*:\\s*\\\"((?:\\\\\\.|[^\\\"])*)\\\"");

    private final PruebaWebSocket juego;

    public EnviarTextoEvento(PruebaWebSocket juego) {
        this.juego = Objects.requireNonNull(juego, "El juego de PruebaWebSocket es obligatorio");
    }

    @Override
    public void hacer(String payload, ContextoEvento contexto) {
        Objects.requireNonNull(contexto, "El contexto de evento es obligatorio");

        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("El payload de ENVIAR_TEXTO no puede estar vacio");
        }

        String jugadorId = extraerObligatorio(payload, PATRON_JUGADOR_ID, "jugadorId");
        String nombreJugador = extraerOpcional(payload, PATRON_NOMBRE_JUGADOR, jugadorId);
        String texto = extraerObligatorio(payload, PATRON_TEXTO, "texto");

        this.juego.registrarTextoJugador(jugadorId, nombreJugador, texto);
    }

    private String extraerObligatorio(String payload, Pattern patron, String campo) {
        Matcher matcher = patron.matcher(payload);
        if (!matcher.find()) {
            throw new IllegalArgumentException("El payload no contiene el campo obligatorio: " + campo);
        }

        String valor = unescapeJson(matcher.group(1)).trim();
        if (valor.isBlank()) {
            throw new IllegalArgumentException("El campo " + campo + " no puede estar vacio");
        }

        return valor;
    }

    private String extraerOpcional(String payload, Pattern patron, String valorPorDefecto) {
        Matcher matcher = patron.matcher(payload);
        if (!matcher.find()) {
            return valorPorDefecto;
        }

        String valor = unescapeJson(matcher.group(1)).trim();
        return valor.isBlank() ? valorPorDefecto : valor;
    }

    private String unescapeJson(String valor) {
        return valor.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}