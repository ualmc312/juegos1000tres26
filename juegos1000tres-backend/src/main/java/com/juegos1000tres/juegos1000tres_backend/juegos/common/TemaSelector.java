package com.juegos1000tres.juegos1000tres_backend.juegos.common;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juegos1000tres.juegos1000tres_backend.ia.RespuestaIA;
import com.juegos1000tres.juegos1000tres_backend.ia.ServicioIA;
import com.juegos1000tres.juegos1000tres_backend.ia.SolicitudIA;

@Service
public class TemaSelector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ServicioIA servicioIA;

    public TemaSelector(ServicioIA servicioIA) {
        this.servicioIA = servicioIA;
    }

    public record ValidacionTema(boolean valido, String temaNormalizado, String mensaje, String raw) {
    }

    public ValidacionTema validarTema(String temaPropuesto, String promptTemplate, String instruccionesSistema, Map<String, Object> metadata) {
        String prompt = String.format(promptTemplate, temaPropuesto);

        RespuestaIA respuesta = this.servicioIA.consultar(SolicitudIA.completa(
                prompt,
                instruccionesSistema,
                null,
                0.2d,
                512,
                metadata == null ? Map.of() : metadata));

        String texto = limpiarRespuestaJson(respuesta.texto());
        try {
            JsonNode json = OBJECT_MAPPER.readTree(texto);
            boolean valido = json.has("valido") && (json.get("valido").isBoolean() ? json.get("valido").asBoolean() : "true".equalsIgnoreCase(json.get("valido").asText()));
            String mensaje = json.has("mensaje") ? json.get("mensaje").asText( valido ? "Tema aceptado" : "Tema no valido") : (valido ? "Tema aceptado" : "Tema no valido");
            String temaNormalizado = json.has("temaNormalizado") ? json.get("temaNormalizado").asText(temaPropuesto.trim()) : temaPropuesto.trim();
            return new ValidacionTema(valido, temaNormalizado, mensaje, texto);
        } catch (JsonProcessingException e) {
            return new ValidacionTema(false, temaPropuesto.trim(), "IA no devolvió JSON válido", textoOrEmpty(respuesta.texto()));
        }
    }

    private static String textoOrEmpty(String s) {
        return s == null ? "" : s;
    }

    private String limpiarRespuestaJson(String texto) {
        if (texto == null) {
            return "";
        }

        String limpio = texto.trim();
        if (limpio.startsWith("```")) {
            limpio = limpio.replaceFirst("^```(?:json)?\\s*", "");
            limpio = limpio.replaceFirst("\\s*```$", "");
        }

        int inicio = limpio.indexOf('{');
        int fin = limpio.lastIndexOf('}');
        if (inicio >= 0 && fin > inicio) {
            return limpio.substring(inicio, fin + 1);
        }

        return limpio;
    }
}
