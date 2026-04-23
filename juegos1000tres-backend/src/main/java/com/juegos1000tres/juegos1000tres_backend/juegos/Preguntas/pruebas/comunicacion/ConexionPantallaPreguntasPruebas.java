package com.juegos1000tres.juegos1000tres_backend.juegos.Preguntas.pruebas.comunicacion;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Conexion;

public final class ConexionPantallaPreguntasPruebas implements Conexion<String> {

    private static final String PAYLOAD_VACIO = "{}";
    private static final int MAX_UPDATES_POR_PANTALLA = 100;

    private final String salaId;
    private final Map<String, Deque<String>> updatesByScreen;

    public ConexionPantallaPreguntasPruebas(String salaId) {
        this.salaId = (salaId == null || salaId.isBlank()) ? "preguntas-pruebas" : salaId;
        this.updatesByScreen = new LinkedHashMap<>();
    }

    @Override
    public void conectar() {
        // No-op para pruebas.
    }

    @Override
    public void desconectar() {
        // No-op para pruebas.
    }

    @Override
    public synchronized void enviar(String payload) {
        String payloadNoVacio = (payload == null || payload.isBlank()) ? PAYLOAD_VACIO : payload;

        for (Deque<String> cola : this.updatesByScreen.values()) {
            while (cola.size() >= MAX_UPDATES_POR_PANTALLA) {
                cola.pollFirst();
            }
            cola.addLast(payloadNoVacio);
        }
    }

    @Override
    public synchronized String recibir() {
        return PAYLOAD_VACIO;
    }

    @Override
    public Class<String> getClasePayload() {
        return String.class;
    }

    @Override
    public String getTipoComunicacion() {
        return "API";
    }

    @Override
    public String getSalaId() {
        return this.salaId;
    }

    @Override
    public String getCanalSala() {
        return "/api/pruebas/preguntas/updates";
    }

    public synchronized Optional<String> obtenerSiguienteActualizacion(String screenId) {
        String id = normalizarScreenId(screenId);
        Deque<String> cola = this.updatesByScreen.computeIfAbsent(id, (_id) -> new ArrayDeque<>());
        return Optional.ofNullable(cola.pollFirst());
    }

    private String normalizarScreenId(String screenId) {
        if (screenId == null || screenId.isBlank()) {
            throw new IllegalArgumentException("Missing query param 'screenId'");
        }

        return screenId.trim();
    }
}
