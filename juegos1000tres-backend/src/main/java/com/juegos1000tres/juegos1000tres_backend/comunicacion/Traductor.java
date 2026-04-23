package com.juegos1000tres.juegos1000tres_backend.comunicacion;

import java.util.Objects;
import java.util.Optional;

public class Traductor<PAYLOAD> {

    private final Conexion<PAYLOAD> conexion;
    private final Envio<PAYLOAD> envio;
    private final Recibo<PAYLOAD> recibo;
    private final Class<PAYLOAD> clasePayload;

    public Traductor(Conexion<PAYLOAD> conexion, Envio<PAYLOAD> envio, Recibo<PAYLOAD> recibo) {
        this.conexion = Objects.requireNonNull(conexion, "La conexion es obligatoria");
        this.envio = Objects.requireNonNull(envio, "La estrategia de envio es obligatoria");
        this.recibo = Objects.requireNonNull(recibo, "La estrategia de recibo es obligatoria");
        this.clasePayload = this.conexion.getClasePayload();

        validarCompatibilidadDePayload(this.clasePayload, this.envio.getClasePayload(), "Envio");
        validarCompatibilidadDePayload(this.clasePayload, this.recibo.getClasePayload(), "Recibo");
    }

    public PAYLOAD traducirEnviableAFormato(Enviable enviable) {
        return envio.traducirEnviableAFormato(enviable);
    }

    public void enviar(Enviable enviable) {
        PAYLOAD payload = traducirEnviableAFormato(enviable);
        conexion.enviar(payload);
    }

    public void enviarPayload(PAYLOAD payload) {
        Objects.requireNonNull(payload, "El payload es obligatorio");
        conexion.enviar(payload);
    }

    public Optional<PAYLOAD> procesar(PAYLOAD payload) {
        Objects.requireNonNull(payload, "El payload es obligatorio");

        ContextoEvento contexto = new ContextoEvento();
        recibo.procesar(payload, contexto);

        return contexto.getRespuesta().map(this::traducirEnviableAFormato);
    }

    public PAYLOAD recibirPayload() {
        return conexion.recibir();
    }

    public Optional<PAYLOAD> recibirYProcesar() {
        PAYLOAD payload = recibirPayload();
        return procesar(payload);
    }

    public Optional<PAYLOAD> recibirProcesarYResponder() {
        Optional<PAYLOAD> respuesta = recibirYProcesar();
        respuesta.ifPresent(conexion::enviar);
        return respuesta;
    }

    public Class<PAYLOAD> getClasePayload() {
        return clasePayload;
    }

    private void validarCompatibilidadDePayload(Class<PAYLOAD> claseBase, Class<PAYLOAD> claseDependencia, String dependencia) {
        if (!Objects.equals(claseBase, claseDependencia)) {
            throw new IllegalArgumentException(
                    "La clase de payload de " + dependencia + " no coincide con la de Conexion");
        }
    }
}