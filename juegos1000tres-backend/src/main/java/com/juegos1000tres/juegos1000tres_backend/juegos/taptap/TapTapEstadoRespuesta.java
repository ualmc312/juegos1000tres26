package com.juegos1000tres.juegos1000tres_backend.juegos.taptap;

import java.util.List;

public record TapTapEstadoRespuesta(
        long inicioEpochMs,
        long duracionMs,
        long restanteMs,
        boolean finalizada,
        String ganadorId,
        List<TapTapPuntuacion> puntuaciones
) {
}
