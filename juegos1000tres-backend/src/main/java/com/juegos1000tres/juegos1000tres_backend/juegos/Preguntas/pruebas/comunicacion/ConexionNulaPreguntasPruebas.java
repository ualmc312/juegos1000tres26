package com.juegos1000tres.juegos1000tres_backend.juegos.Preguntas.pruebas.comunicacion;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Conexion;

public final class ConexionNulaPreguntasPruebas implements Conexion<String> {

    private final String salaId;
    private String ultimoPayload;

    public ConexionNulaPreguntasPruebas(String salaId) {
        this.salaId = (salaId == null || salaId.isBlank()) ? "preguntas-pruebas" : salaId;
        this.ultimoPayload = "{}";
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
    public void enviar(String payload) {
        this.ultimoPayload = (payload == null || payload.isBlank()) ? "{}" : payload;
    }

    @Override
    public String recibir() {
        return this.ultimoPayload;
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
        return "/api/pruebas/preguntas";
    }
}
