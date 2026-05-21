package com.juegos1000tres.juegos1000tres_backend.ia;

import java.util.Map;

public record SolicitudIA(
        String prompt,
        String instruccionesSistema,
        String modelo,
        Double temperatura,
        Integer maxTokens,
        Map<String, Object> metadatos) {

    public SolicitudIA {
        prompt = limpiarTextoObligatorio(prompt, "El prompt es obligatorio");
        instruccionesSistema = limpiarTextoOpcional(instruccionesSistema);
        modelo = limpiarTextoOpcional(modelo);
        validarTemperatura(temperatura);
        validarMaxTokens(maxTokens);
        metadatos = metadatos == null || metadatos.isEmpty() ? Map.of() : Map.copyOf(metadatos);
    }

    public static SolicitudIA desdePrompt(String prompt) {
        return new SolicitudIA(prompt, null, null, null, null, Map.of());
    }

    public static SolicitudIA desdePrompt(String prompt, String instruccionesSistema, String modelo) {
        return new SolicitudIA(prompt, instruccionesSistema, modelo, null, null, Map.of());
    }

    public static SolicitudIA completa(
            String prompt,
            String instruccionesSistema,
            String modelo,
            Double temperatura,
            Integer maxTokens,
            Map<String, Object> metadatos) {
        return new SolicitudIA(prompt, instruccionesSistema, modelo, temperatura, maxTokens, metadatos);
    }

    private static String limpiarTextoObligatorio(String texto, String mensajeError) {
        String textoLimpio = limpiarTextoOpcional(texto);
        if (textoLimpio == null) {
            throw new IllegalArgumentException(mensajeError);
        }

        return textoLimpio;
    }

    private static String limpiarTextoOpcional(String texto) {
        if (texto == null) {
            return null;
        }

        String textoLimpio = texto.trim();
        return textoLimpio.isEmpty() ? null : textoLimpio;
    }

    private static void validarTemperatura(Double temperatura) {
        if (temperatura == null) {
            return;
        }

        if (temperatura < 0.0d || temperatura > 2.0d) {
            throw new IllegalArgumentException("La temperatura debe estar entre 0 y 2");
        }
    }

    private static void validarMaxTokens(Integer maxTokens) {
        if (maxTokens == null) {
            return;
        }

        if (maxTokens < 1) {
            throw new IllegalArgumentException("El numero maximo de tokens debe ser mayor que cero");
        }
    }
}