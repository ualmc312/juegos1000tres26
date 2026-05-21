package com.juegos1000tres.juegos1000tres_backend.juegos.HablameDeTi;

import java.util.Objects;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.ContextoEvento;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Evento;

public class IniciarPartidaEvento implements Evento<String> {

    private final HablameDeTiJuego juego;

    public IniciarPartidaEvento(HablameDeTiJuego juego) {
        this.juego = Objects.requireNonNull(juego, "El juego es obligatorio");
    }

    @Override
    public void hacer(String payload, ContextoEvento contexto) {
        this.juego.iniciarPartidaDesdePayload(payload);
    }
}