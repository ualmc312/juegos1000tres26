package com.juegos1000tres.juegos1000tres_backend.ia;

import java.util.Objects;

public abstract class ClienteApiIA implements ServicioIA {

    @Override
    public final RespuestaIA consultar(SolicitudIA solicitud) {
        SolicitudIA solicitudNoNula = Objects.requireNonNull(solicitud, "La solicitud de IA es obligatoria");
        validarSolicitud(solicitudNoNula);

        RespuestaIA respuesta = ejecutarConsulta(solicitudNoNula);
        return Objects.requireNonNull(respuesta, "La respuesta de IA no puede ser nula");
    }

    protected void validarSolicitud(SolicitudIA solicitud) {
        Objects.requireNonNull(solicitud, "La solicitud de IA es obligatoria");
    }

    protected abstract RespuestaIA ejecutarConsulta(SolicitudIA solicitud);
}