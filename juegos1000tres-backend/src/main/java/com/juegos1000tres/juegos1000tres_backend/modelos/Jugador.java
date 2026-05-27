package com.juegos1000tres.juegos1000tres_backend.modelos;

import java.util.Objects;
import java.util.UUID;

public class Jugador {

    private final UUID id;
    private final String nombre;
    private final String usuarioId;
    private boolean conectado;
    private int victorias;
    private int puntuacion;

    public Jugador(String nombre) {
        this(UUID.randomUUID(), nombre, null);
    }

    public Jugador(UUID id, String nombre) {
        this(id, nombre, null);
    }

    public Jugador(String nombre, String usuarioId) {
        this(UUID.randomUUID(), nombre, usuarioId);
    }

    public Jugador(UUID id, String nombre, String usuarioId) {
        this.id = Objects.requireNonNull(id, "El id del jugador es obligatorio");

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del jugador no puede estar vacio");
        }

        this.nombre = nombre.trim();
        this.usuarioId = (usuarioId == null || usuarioId.isBlank()) ? null : usuarioId.trim();
        this.conectado = true;
        this.victorias = 0;
        this.puntuacion = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public boolean isConectado() {
        return conectado;
    }

    public int getVictorias() {
        return victorias;
    }

    public int getPuntuacion() {
        return puntuacion;
    }

    public void conectar() {
        this.conectado = true;
    }

    public void desconectar() {
        this.conectado = false;
    }

    public void sumarVictoria() {
        this.victorias += 1;
    }

    public void sumarPuntos(int puntos) {
        if (puntos < 0) {
            throw new IllegalArgumentException("No se pueden sumar puntos negativos");
        }

        this.puntuacion += puntos;
    }

    public void establecerPuntuacion(int puntuacion) {
        if (puntuacion < 0) {
            throw new IllegalArgumentException("No se puede establecer una puntuacion negativa");
        }

        this.puntuacion = puntuacion;
    }

    public void reiniciarPuntuacion() {
        this.puntuacion = 0;
    }
}
