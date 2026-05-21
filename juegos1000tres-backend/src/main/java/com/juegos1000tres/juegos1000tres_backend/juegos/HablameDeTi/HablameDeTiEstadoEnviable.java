package com.juegos1000tres.juegos1000tres_backend.juegos.HablameDeTi;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;

public class HablameDeTiEstadoEnviable extends Enviable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private Map<String, Object> estado;

    public HablameDeTiEstadoEnviable() {
        this.estado = new LinkedHashMap<>();
    }

    public HablameDeTiEstadoEnviable(Map<String, Object> estado) {
        this.estado = estado == null ? new LinkedHashMap<>() : new LinkedHashMap<>(estado);
    }

    @Override
    public Object out() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this.estado);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("No se pudo serializar el estado de HablameDeTi", error);
        }
    }

    @Override
    public void in(Object entrada) {
        if (entrada == null) {
            this.estado = new LinkedHashMap<>();
            return;
        }

        if (!(entrada instanceof String json)) {
            throw new IllegalArgumentException("Se esperaba un String para deserializar el estado de HablameDeTi");
        }

        try {
            this.estado = OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("No se pudo deserializar el estado de HablameDeTi", error);
        }
    }

    public Map<String, Object> getEstado() {
        return new LinkedHashMap<>(this.estado);
    }
}