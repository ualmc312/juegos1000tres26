package com.juegos1000tres.juegos1000tres_backend.modelos;

import java.util.Objects;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;

public abstract class Juego {

    private final int numeroJugadores;
    private final boolean necesitaPantalla;
    protected final Traductor<?> conexionJugadores;
    protected final Traductor<?> conexionPantalla;

    protected Juego(int numeroJugadores, boolean necesitaPantalla, Traductor<?> conexionJugadores,
            Traductor<?> conexionPantalla) {
        if (numeroJugadores <= 0) {
            throw new IllegalArgumentException("El numero de jugadores debe ser mayor que cero");
        }

        this.numeroJugadores = numeroJugadores;
        this.necesitaPantalla = necesitaPantalla;
        this.conexionJugadores = Objects.requireNonNull(conexionJugadores,
                "El traductor de conexion de jugadores es obligatorio");
        this.conexionPantalla = Objects.requireNonNull(conexionPantalla,
                "El traductor de conexion de pantalla es obligatorio");
    }

    public int getNumeroJugadores() {
        return numeroJugadores;
    }

    public boolean isNecesitaPantalla() {
        return necesitaPantalla;
    }

    public abstract void procesarMensajeEntrante(Enviable mensaje);

    public abstract void iniciar();

    public abstract void terminar();
}
