package com.juegos1000tres.juegos1000tres_backend.juegos.Preguntas;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;

public class PreguntasEstadoEnviable extends Enviable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private Map<String, Object> estado;

    public PreguntasEstadoEnviable() {
        this.estado = new LinkedHashMap<>();
    }

    public PreguntasEstadoEnviable(Map<String, Object> estado) {
        this.estado = estado == null ? new LinkedHashMap<>() : new LinkedHashMap<>(estado);
    }

    @Override
    public Object out() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this.estado);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo serializar el estado de Preguntas", error);
        }
    }

    @Override
    public void in(Object entrada) {
        if (entrada == null) {
            this.estado = new LinkedHashMap<>();
            return;
        }

        if (!(entrada instanceof String json)) {
            throw new IllegalArgumentException("Se esperaba un String para deserializar el estado de Preguntas");
        }

        try {
            this.estado = OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception error) {
            throw new IllegalArgumentException("No se pudo deserializar el estado de Preguntas", error);
        }
    }

    public Map<String, Object> getEstado() {
        return Map.copyOf(this.estado);
    }
}
