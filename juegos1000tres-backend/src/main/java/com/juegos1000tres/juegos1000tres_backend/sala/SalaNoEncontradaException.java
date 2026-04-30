package com.juegos1000tres.juegos1000tres_backend.sala;

public class SalaNoEncontradaException extends RuntimeException {

    public SalaNoEncontradaException() {
        super("Sala no encontrada");
    }
}
