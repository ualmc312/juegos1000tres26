package com.juegos1000tres.juegos1000tres_backend.modelos;

import java.util.Objects;
import java.util.UUID;

public class Jugador {

    private final UUID id;
    private final String nombre;
    private boolean conectado;
    private int puntuacion;

    public Jugador(String nombre) {
        this(UUID.randomUUID(), nombre);
    }

    public Jugador(UUID id, String nombre) {
        this.id = Objects.requireNonNull(id, "El id del jugador es obligatorio");

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del jugador no puede estar vacio");
        }

        this.nombre = nombre.trim();
        this.conectado = true;
        this.puntuacion = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isConectado() {
        return conectado;
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

    public void sumarPuntos(int puntos) {
        if (puntos < 0) {
            throw new IllegalArgumentException("No se pueden sumar puntos negativos");
        }

        this.puntuacion += puntos;
    }

    public void reiniciarPuntuacion() {
        this.puntuacion = 0;
    }
}
