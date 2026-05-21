package com.juegos1000tres.juegos1000tres_backend.juegos.AdivinaElPersonaje;

import java.util.Objects;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.ContextoEvento;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Evento;

public class IntentarAdivinarEvento implements Evento<String> {

    private final AdivinaElPersonajeJuego juego;

    public IntentarAdivinarEvento(AdivinaElPersonajeJuego juego) {
        this.juego = Objects.requireNonNull(juego, "El juego es obligatorio");
    }

    @Override
    public void hacer(String payload, ContextoEvento contexto) {
        this.juego.intentarAdivinarDesdePayload(payload);
    }
}