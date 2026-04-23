package com.juegos1000tres.juegos1000tres_backend.comunicacion;

public interface Evento<PAYLOAD> {

    void hacer(PAYLOAD payload, ContextoEvento contexto);
}
