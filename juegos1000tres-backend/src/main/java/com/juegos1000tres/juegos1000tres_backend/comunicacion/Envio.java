package com.juegos1000tres.juegos1000tres_backend.comunicacion;

public interface Envio<PAYLOAD> {

    PAYLOAD traducirEnviableAFormato(Enviable enviable);

    Class<PAYLOAD> getClasePayload();
}