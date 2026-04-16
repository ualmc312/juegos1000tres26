package com.juegos1000tres.juegos1000tres_backend.comunicacion.implementaciones;

import java.util.Objects;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Conexion;

public class ApiConexion implements Conexion<String> {

    private static final String TIPO_COMUNICACION = "API";

    private boolean conectada;
    private String ultimoMensaje;

    public ApiConexion() {
        this.conectada = false;
        this.ultimoMensaje = "{}";
    }

    @Override
    public void conectar() {
        this.conectada = true;
    }

    @Override
    public void desconectar() {
        this.conectada = false;
    }

    @Override
    public void enviar(String payload) {
        validarConexionActiva();
        this.ultimoMensaje = Objects.requireNonNull(payload, "El payload no puede ser nulo");
    }

    @Override
    public String recibir() {
        validarConexionActiva();
        return ultimoMensaje;
    }

    @Override
    public Class<String> getClasePayload() {
        return String.class;
    }

    @Override
    public String getTipoComunicacion() {
        return TIPO_COMUNICACION;
    }

    private void validarConexionActiva() {
        if (!conectada) {
            throw new IllegalStateException("La conexion API debe estar activa para enviar o recibir");
        }
    }
}