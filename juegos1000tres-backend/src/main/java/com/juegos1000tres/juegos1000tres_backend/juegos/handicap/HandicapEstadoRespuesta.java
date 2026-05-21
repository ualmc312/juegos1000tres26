package com.juegos1000tres.juegos1000tres_backend.juegos.handicap;

import java.util.List;

import com.juegos1000tres.juegos1000tres_backend.sala.JugadorRespuesta;

public record HandicapEstadoRespuesta(
        String fase,
        List<JugadorRespuesta> jugadores,
        List<JugadorRespuesta> ganadores,
        long tiempoRestanteMs,
        boolean hostBloqueado
) {
}
