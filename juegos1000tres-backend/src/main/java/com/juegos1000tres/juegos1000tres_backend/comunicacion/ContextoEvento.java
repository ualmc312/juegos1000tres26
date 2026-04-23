package com.juegos1000tres.juegos1000tres_backend.comunicacion;

import java.util.Objects;
import java.util.Optional;

public final class ContextoEvento {

    private Enviable respuesta;

    public void enviar(Enviable enviable) {
        if (this.respuesta != null) {
            throw new IllegalStateException("Solo se puede establecer una respuesta principal por evento");
        }

        this.respuesta = Objects.requireNonNull(enviable, "El enviable de respuesta es obligatorio");
    }

    public boolean tieneRespuesta() {
        return this.respuesta != null;
    }

    public Optional<Enviable> getRespuesta() {
        return Optional.ofNullable(this.respuesta);
    }
}
