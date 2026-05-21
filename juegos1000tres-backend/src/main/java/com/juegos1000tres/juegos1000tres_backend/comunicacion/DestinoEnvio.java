package com.juegos1000tres.juegos1000tres_backend.comunicacion;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class DestinoEnvio {

    public enum Tipo {
        GLOBAL,
        PANTALLA,
        JUGADOR,
        JUGADORES,
        TODOS
    }

    private final Tipo tipo;
    private final String jugadorId;
    private final Set<String> jugadoresIds;

    private DestinoEnvio(Tipo tipo, String jugadorId, Set<String> jugadoresIds) {
        this.tipo = Objects.requireNonNull(tipo, "El tipo de destino es obligatorio");
        this.jugadorId = jugadorId;
        this.jugadoresIds = jugadoresIds == null ? Set.of() : Set.copyOf(jugadoresIds);
    }

    public static DestinoEnvio global() {
        return new DestinoEnvio(Tipo.GLOBAL, null, Set.of());
    }

    public static DestinoEnvio pantalla() {
        return new DestinoEnvio(Tipo.PANTALLA, null, Set.of());
    }

    public static DestinoEnvio jugador(String jugadorId) {
        String jugadorIdLimpio = validarJugadorId(jugadorId);
        return new DestinoEnvio(Tipo.JUGADOR, jugadorIdLimpio, Set.of(jugadorIdLimpio));
    }

    public static DestinoEnvio jugadores(Set<String> jugadoresIds) {
        Set<String> idsLimpios = limpiarJugadores(jugadoresIds);
        return new DestinoEnvio(Tipo.JUGADORES, null, idsLimpios);
    }

    public static DestinoEnvio todos() {
        return new DestinoEnvio(Tipo.TODOS, null, Set.of());
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getJugadorId() {
        return jugadorId;
    }

    public Set<String> getJugadoresIds() {
        return Collections.unmodifiableSet(this.jugadoresIds);
    }

    public boolean esGlobal() {
        return this.tipo == Tipo.GLOBAL || this.tipo == Tipo.TODOS;
    }

    public boolean esJugador() {
        return this.tipo == Tipo.JUGADOR;
    }

    public boolean esVariosJugadores() {
        return this.tipo == Tipo.JUGADORES;
    }

    public boolean esPantalla() {
        return this.tipo == Tipo.PANTALLA;
    }

    private static String validarJugadorId(String jugadorId) {
        if (jugadorId == null || jugadorId.isBlank()) {
            throw new IllegalArgumentException("El id de jugador es obligatorio");
        }

        return jugadorId.trim();
    }

    private static Set<String> limpiarJugadores(Set<String> jugadoresIds) {
        if (jugadoresIds == null || jugadoresIds.isEmpty()) {
            return Set.of();
        }

        Set<String> resultado = new LinkedHashSet<>();
        for (String jugadorId : jugadoresIds) {
            if (jugadorId != null && !jugadorId.isBlank()) {
                resultado.add(jugadorId.trim());
            }
        }

        return Set.copyOf(resultado);
    }
}