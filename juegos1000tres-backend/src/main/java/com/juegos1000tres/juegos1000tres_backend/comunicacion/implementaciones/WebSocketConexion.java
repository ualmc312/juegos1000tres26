package com.juegos1000tres.juegos1000tres_backend.comunicacion.implementaciones;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Conexion;

public class WebSocketConexion implements Conexion<String> {

    private static final String TIPO_COMUNICACION = "WEBSOCKET";

    private final Queue<String> bufferMensajes;
    private boolean conectada;

    public WebSocketConexion() {
        this.bufferMensajes = new ConcurrentLinkedQueue<>();
        this.conectada = false;
    }

    @Override
    public void conectar() {
        this.conectada = true;
    }

    @Override
    public void desconectar() {
        this.conectada = false;
        this.bufferMensajes.clear();
    }

    @Override
    public void enviar(String payload) {
        validarConexionActiva();
        this.bufferMensajes.offer(Objects.requireNonNull(payload, "El payload no puede ser nulo"));
    }

    @Override
    public String recibir() {
        validarConexionActiva();
        String mensaje = this.bufferMensajes.poll();
        return mensaje == null ? "{}" : mensaje;
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
            throw new IllegalStateException("La conexion WebSocket debe estar activa para enviar o recibir");
        }
    }
}