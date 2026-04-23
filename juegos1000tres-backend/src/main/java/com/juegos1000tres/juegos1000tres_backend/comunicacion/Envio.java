package com.juegos1000tres.juegos1000tres_backend.comunicacion;

import java.util.Objects;
import java.util.function.Function;

public class Envio<PAYLOAD> {

    private final Class<PAYLOAD> clasePayload;
    private final Function<Enviable, PAYLOAD> traductor;

    public Envio(Class<PAYLOAD> clasePayload, Function<Enviable, PAYLOAD> traductor) {
        this.clasePayload = Objects.requireNonNull(clasePayload, "La clase de payload es obligatoria");
        this.traductor = Objects.requireNonNull(traductor, "La funcion de traduccion es obligatoria");
    }

    public PAYLOAD traducirEnviableAFormato(Enviable enviable) {
        return this.traductor.apply(enviable);
    }

    public Class<PAYLOAD> getClasePayload() {
        return this.clasePayload;
    }

    public static Envio<String> paraStringDesdeOut() {
        return new Envio<>(String.class, (enviable) -> {
            Enviable enviableNoNulo = Objects.requireNonNull(enviable, "El enviable es obligatorio");

            Object salida = enviableNoNulo.out();
            if (!(salida instanceof String json)) {
                throw new IllegalArgumentException("Envio<String> requiere que Enviable.out() devuelva String");
            }

            return json;
        });
    }
}