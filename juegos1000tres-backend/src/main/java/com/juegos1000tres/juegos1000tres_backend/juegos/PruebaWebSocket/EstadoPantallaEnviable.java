package com.juegos1000tres.juegos1000tres_backend.juegos.PruebaWebSocket;

import java.util.ArrayList;
import java.util.List;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;

public final class EstadoPantallaEnviable extends Enviable {

    private List<JugadorPalabrasDTO> jugadores;

    public EstadoPantallaEnviable() {
        this(List.of());
    }

    public EstadoPantallaEnviable(List<JugadorPalabrasDTO> jugadores) {
        this.jugadores = jugadores == null ? new ArrayList<>() : new ArrayList<>(jugadores);
    }

    @Override
    public Object out() {
        StringBuilder json = new StringBuilder();
        json.append("{\"comando\":\"")
                .append(PruebaWebSocket.COMANDO_ESTADO_PANTALLA)
                .append("\",\"jugadores\":[");

        for (int i = 0; i < this.jugadores.size(); i++) {
            JugadorPalabrasDTO jugador = this.jugadores.get(i);
            if (i > 0) {
                json.append(',');
            }

            json.append("{\"jugadorId\":\"")
                    .append(escapeJson(jugador.jugadorId()))
                    .append("\",\"nombreJugador\":\"")
                    .append(escapeJson(jugador.nombreJugador()))
                    .append("\",\"palabras\":[");

            List<String> palabras = jugador.palabras();
            for (int j = 0; j < palabras.size(); j++) {
                if (j > 0) {
                    json.append(',');
                }
                json.append("\"").append(escapeJson(palabras.get(j))).append("\"");
            }

            json.append("]}");
        }

        json.append("]}");
        return json.toString();
    }

    @Override
    public void in(Object entrada) {
        this.jugadores = new ArrayList<>();
    }

    public List<JugadorPalabrasDTO> getJugadores() {
        return List.copyOf(this.jugadores);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record JugadorPalabrasDTO(String jugadorId, String nombreJugador, List<String> palabras) {
        public JugadorPalabrasDTO {
            if (palabras == null) {
                palabras = List.of();
            } else {
                palabras = List.copyOf(palabras);
            }
        }
    }
}