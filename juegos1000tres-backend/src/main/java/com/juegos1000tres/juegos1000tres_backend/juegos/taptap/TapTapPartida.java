package com.juegos1000tres.juegos1000tres_backend.juegos.taptap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class TapTapPartida {

    private final long inicioEpochMs;
    private final long duracionMs;
    private final Map<String, Integer> puntos = new ConcurrentHashMap<>();
    private boolean finalizada;
    private String ganadorId;

    TapTapPartida(long inicioEpochMs, long duracionMs) {
        this.inicioEpochMs = inicioEpochMs;
        this.duracionMs = duracionMs;
        this.finalizada = false;
        this.ganadorId = null;
    }

    long getInicioEpochMs() {
        return inicioEpochMs;
    }

    long getDuracionMs() {
        return duracionMs;
    }

    synchronized boolean estaFinalizada() {
        return finalizada;
    }

    long getRestanteMs(long ahora) {
        long fin = inicioEpochMs + duracionMs;
        return Math.max(0, fin - ahora);
    }

    synchronized boolean estaEnCurso(long ahora) {
        return !finalizada && getRestanteMs(ahora) > 0 && ahora >= inicioEpochMs;
    }

    synchronized int sumarPunto(String jugadorId, long ahora) {
        if (!estaEnCurso(ahora)) {
            return obtenerPuntos(jugadorId);
        }

        return puntos.merge(jugadorId, 1, Integer::sum);
    }

    synchronized int obtenerPuntos(String jugadorId) {
        return puntos.getOrDefault(jugadorId, 0);
    }

    Map<String, Integer> getPuntos() {
        return puntos;
    }

    synchronized void finalizar(String ganadorId) {
        if (finalizada) {
            return;
        }

        this.finalizada = true;
        this.ganadorId = ganadorId;
    }

    synchronized String getGanadorId() {
        return ganadorId;
    }
}
