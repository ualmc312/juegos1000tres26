package com.juegos1000tres.juegos1000tres_backend.juegos.Dibujo;

import java.util.Objects;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.ContextoEvento;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Evento;

public class ProponerTemaEvento implements Evento<String> {

    private final DibujoJuego juego;

    public ProponerTemaEvento(DibujoJuego juego) {
        this.juego = Objects.requireNonNull(juego, "El juego es obligatorio");
    }

    @Override
    public void hacer(String payload, ContextoEvento contexto) {
        this.juego.proponerTemaDesdePayload(payload);
    }
}
