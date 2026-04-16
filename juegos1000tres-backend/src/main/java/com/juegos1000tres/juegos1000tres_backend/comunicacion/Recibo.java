package com.juegos1000tres.juegos1000tres_backend.comunicacion;

public interface Recibo<PAYLOAD> {

    <T extends Enviable> T traducirFormatoAEnviable(PAYLOAD payload, Class<T> tipoEnviable);

    Class<PAYLOAD> getClasePayload();
}