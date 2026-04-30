package com.juegos1000tres.juegos1000tres_backend.sala;

import java.util.List;

public record SalaRespuesta(
        String uuid,
        List<JugadorRespuesta> jugadores,
        String hostId,
        String pantallaId,
        String juegoActual,
        String jugadorId
) {
}
