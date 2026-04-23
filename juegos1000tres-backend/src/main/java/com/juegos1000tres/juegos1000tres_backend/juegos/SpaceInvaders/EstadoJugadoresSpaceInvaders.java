package com.juegos1000tres.juegos1000tres_backend.juegos.SpaceInvaders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;

public class EstadoJugadoresSpaceInvaders extends Enviable {

    private static final String COMANDO_ESTADO_JUGADORES = "ESTADO_JUGADORES";

    private static final Pattern JUGADOR_JSON = Pattern.compile(
            "\\{\\\"jugadorId\\\":\\\"([^\\\"]+)\\\",\\\"nombreJugador\\\":\\\"((?:\\\\\\\\.|[^\\\"])*)\\\",\\\"puntuacion\\\":(-?\\d+),\\\"muerto\\\":(true|false)\\}");

    private List<EstadoJugadorDTO> jugadores;

    public EstadoJugadoresSpaceInvaders() {
        this.jugadores = new ArrayList<>();
    }

    public EstadoJugadoresSpaceInvaders(Collection<EstadoJugadorDTO> estadoJugadores) {
        this();
        this.jugadores.addAll(estadoJugadores);
    }

    public List<EstadoJugadorDTO> getJugadores() {
        return List.copyOf(jugadores);
    }

    @Override
    public Object out() {
        StringBuilder json = new StringBuilder();
        json.append("{\"comando\":\"")
            .append(COMANDO_ESTADO_JUGADORES)
            .append("\",\"jugadores\":[");

        for (int i = 0; i < this.jugadores.size(); i++) {
            EstadoJugadorDTO jugador = this.jugadores.get(i);
            if (i > 0) {
                json.append(',');
            }

            json.append("{\"jugadorId\":\"")
                    .append(jugador.getJugadorId())
                    .append("\",\"nombreJugador\":\"")
                    .append(escapeJson(jugador.getNombreJugador()))
                    .append("\",\"puntuacion\":")
                    .append(jugador.getPuntuacion())
                    .append(",\"muerto\":")
                    .append(jugador.isMuerto())
                    .append('}');
        }

        json.append("]}");
        return json.toString();
    }

    @Override
    public void in(Object entrada) {
        if (entrada == null) {
            this.jugadores = new ArrayList<>();
            return;
        }

        if (!(entrada instanceof String json)) {
            throw new IllegalArgumentException("Se esperaba un String para deserializar el estado de jugadores");
        }

        if (json == null || json.isBlank()) {
            this.jugadores = new ArrayList<>();
            return;
        }

        List<EstadoJugadorDTO> jugadoresParseados = new ArrayList<>();
        Matcher matcher = JUGADOR_JSON.matcher(json);
        while (matcher.find()) {
            UUID jugadorId = UUID.fromString(matcher.group(1));
            String nombreJugador = unescapeJson(matcher.group(2));
            int puntuacion = Integer.parseInt(matcher.group(3));
            boolean muerto = Boolean.parseBoolean(matcher.group(4));
            jugadoresParseados.add(new EstadoJugadorDTO(jugadorId, nombreJugador, puntuacion, muerto));
        }

        if (jugadoresParseados.isEmpty() && !json.contains("\"jugadores\":[]")) {
            throw new IllegalArgumentException("No se pudo deserializar el estado de jugadores");
        }

        this.jugadores = jugadoresParseados;
    }

    public void setJugadores(List<EstadoJugadorDTO> jugadores) {
        this.jugadores = jugadores == null ? new ArrayList<>() : new ArrayList<>(jugadores);
    }

    private static String escapeJson(String valor) {
        if (valor == null) {
            return "";
        }

        return valor
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static String unescapeJson(String valor) {
        if (valor == null) {
            return "";
        }

        return valor
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public static class EstadoJugadorDTO {

        private UUID jugadorId;
        private String nombreJugador;
        private int puntuacion;
        private boolean muerto;

        public EstadoJugadorDTO() {
            // Constructor vacio requerido por Jackson
        }

        public EstadoJugadorDTO(UUID jugadorId, String nombreJugador, int puntuacion, boolean muerto) {
            this.jugadorId = jugadorId;
            this.nombreJugador = nombreJugador;
            this.puntuacion = puntuacion;
            this.muerto = muerto;
        }

        public UUID getJugadorId() {
            return jugadorId;
        }

        public void setJugadorId(UUID jugadorId) {
            this.jugadorId = jugadorId;
        }

        public String getNombreJugador() {
            return nombreJugador;
        }

        public void setNombreJugador(String nombreJugador) {
            this.nombreJugador = nombreJugador;
        }

        public int getPuntuacion() {
            return puntuacion;
        }

        public void setPuntuacion(int puntuacion) {
            this.puntuacion = puntuacion;
        }

        public boolean isMuerto() {
            return muerto;
        }

        public void setMuerto(boolean muerto) {
            this.muerto = muerto;
        }
    }
}
