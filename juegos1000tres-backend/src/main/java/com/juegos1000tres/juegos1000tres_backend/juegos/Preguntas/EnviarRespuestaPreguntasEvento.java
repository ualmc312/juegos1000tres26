package com.juegos1000tres.juegos1000tres_backend.juegos.Preguntas;

import java.util.Objects;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.ContextoEvento;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Evento;

public class EnviarRespuestaPreguntasEvento implements Evento<String> {

    private final PreguntasJuego juego;

    public EnviarRespuestaPreguntasEvento(PreguntasJuego juego) {
        this.juego = Objects.requireNonNull(juego, "El juego es obligatorio");
    }

    @Override
    public void hacer(String payload, ContextoEvento contexto) {
        this.juego.enviarRespuestaDesdePayload(payload, contexto);
    }
}
