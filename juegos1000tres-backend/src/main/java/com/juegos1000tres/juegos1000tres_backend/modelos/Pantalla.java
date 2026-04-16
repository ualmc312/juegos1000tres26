package com.juegos1000tres.juegos1000tres_backend.modelos;

import java.time.Instant;

public class Pantalla {

    private String nombreJuego;
    private String estadoPartida;
    private int rondaActual;
    private int jugadoresConectados;
    private Instant ultimaActualizacion;

    public Pantalla(String nombreJuego) {
        this.nombreJuego = normalizarTextoObligatorio(nombreJuego, "El nombre del juego no puede estar vacio");
        this.estadoPartida = "Esperando jugadores";
        this.rondaActual = 1;
        this.jugadoresConectados = 0;
        this.ultimaActualizacion = Instant.now();
    }

    public String getNombreJuego() {
        return nombreJuego;
    }

    public void setNombreJuego(String nombreJuego) {
        this.nombreJuego = normalizarTextoObligatorio(nombreJuego, "El nombre del juego no puede estar vacio");
        marcarActualizacion();
    }

    public String getEstadoPartida() {
        return estadoPartida;
    }

    public int getRondaActual() {
        return rondaActual;
    }

    public int getJugadoresConectados() {
        return jugadoresConectados;
    }

    public Instant getUltimaActualizacion() {
        return ultimaActualizacion;
    }

    public void actualizarEstadoPartida(String estadoPartida) {
        this.estadoPartida = normalizarTextoObligatorio(estadoPartida, "El estado de partida no puede estar vacio");
        marcarActualizacion();
    }

    public void avanzarRonda() {
        this.rondaActual++;
        marcarActualizacion();
    }

    public void setRondaActual(int rondaActual) {
        if (rondaActual < 1) {
            throw new IllegalArgumentException("La ronda actual debe ser mayor o igual a 1");
        }

        this.rondaActual = rondaActual;
        marcarActualizacion();
    }

    public void actualizarJugadoresConectados(int jugadoresConectados) {
        if (jugadoresConectados < 0) {
            throw new IllegalArgumentException("La cantidad de jugadores conectados no puede ser negativa");
        }

        this.jugadoresConectados = jugadoresConectados;
        marcarActualizacion();
    }

    private void marcarActualizacion() {
        this.ultimaActualizacion = Instant.now();
    }

    private static String normalizarTextoObligatorio(String valor, String mensajeError) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensajeError);
        }

        return valor.trim();
    }
}
