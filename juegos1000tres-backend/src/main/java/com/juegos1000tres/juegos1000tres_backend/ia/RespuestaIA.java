package com.juegos1000tres.juegos1000tres_backend.ia;

import java.util.Map;

public record RespuestaIA(
        String texto,
        String modelo,
        Map<String, Object> metadatos) {

    public RespuestaIA {
        texto = texto == null ? null : texto.trim();
        modelo = modelo == null ? null : modelo.trim();
        metadatos = metadatos == null || metadatos.isEmpty() ? Map.of() : Map.copyOf(metadatos);
    }

    public static RespuestaIA deTexto(String texto) {
        return new RespuestaIA(texto, null, Map.of());
    }

    public static RespuestaIA completa(String texto, String modelo, Map<String, Object> metadatos) {
        return new RespuestaIA(texto, modelo, metadatos);
    }
}