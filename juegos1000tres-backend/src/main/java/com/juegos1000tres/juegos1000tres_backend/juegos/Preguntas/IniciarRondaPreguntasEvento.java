package com.juegos1000tres.juegos1000tres_backend.juegos.Preguntas;

import java.util.Objects;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.ContextoEvento;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Evento;

public class IniciarRondaPreguntasEvento implements Evento<String> {

    private final PreguntasJuego juego;

    public IniciarRondaPreguntasEvento(PreguntasJuego juego) {
        this.juego = Objects.requireNonNull(juego, "El juego es obligatorio");
    }

    @Override
    public void hacer(String payload, ContextoEvento contexto) {
        this.juego.iniciarRondaDesdePayload(payload, contexto);
    }
}
