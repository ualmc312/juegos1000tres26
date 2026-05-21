package com.juegos1000tres.juegos1000tres_backend.ia;

public interface ServicioIA {

    RespuestaIA consultar(SolicitudIA solicitud);

    default RespuestaIA consultar(String prompt) {
        return consultar(SolicitudIA.desdePrompt(prompt));
    }
}