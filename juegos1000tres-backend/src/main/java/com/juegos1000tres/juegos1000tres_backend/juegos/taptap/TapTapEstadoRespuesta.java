package com.juegos1000tres.juegos1000tres_backend.juegos.taptap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TapTapEstadoRespuesta(
        long inicioEpochMs,
        long duracionMs,
        long restanteMs,
        boolean finalizada,
        String ganadorId,
        List<TapTapPuntuacion> puntuaciones
) {
    public Map<String, Object> toMap() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("comando", "ESTADO");
        mapa.put("inicioEpochMs", this.inicioEpochMs);
        mapa.put("duracionMs", this.duracionMs);
        mapa.put("restanteMs", this.restanteMs);
        mapa.put("finalizada", this.finalizada);
        mapa.put("ganadorId", this.ganadorId);
        mapa.put("puntuaciones", this.puntuaciones);
        return mapa;
    }
}
