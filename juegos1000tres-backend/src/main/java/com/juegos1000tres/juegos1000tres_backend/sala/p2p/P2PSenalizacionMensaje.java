package com.juegos1000tres.juegos1000tres_backend.sala.p2p;

public record P2PSenalizacionMensaje(
        String tipo,
        String salaId,
        String origenId,
        String destinoId,
        String payload,
        long timestamp) {
}