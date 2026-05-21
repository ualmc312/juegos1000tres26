package com.juegos1000tres.juegos1000tres_backend.ia.google;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.juegos1000tres.juegos1000tres_backend.ia.ClienteApiIA;
import com.juegos1000tres.juegos1000tres_backend.ia.ConfiguracionIA;
import com.juegos1000tres.juegos1000tres_backend.ia.RespuestaIA;
import com.juegos1000tres.juegos1000tres_backend.ia.ServicioIA;
import com.juegos1000tres.juegos1000tres_backend.ia.SolicitudIA;

@Service
@ConditionalOnMissingBean(ServicioIA.class)
public class GeminiFlashClienteIA extends ClienteApiIA {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final ConfiguracionIA configuracionIA;

    public GeminiFlashClienteIA(ConfiguracionIA configuracionIA) {
        this.configuracionIA = configuracionIA;
    }

    @Override
    protected void validarSolicitud(SolicitudIA solicitud) {
        super.validarSolicitud(solicitud);

        if (!configuracionIA.tieneClaveGoogleAiStudio()) {
            throw new IllegalStateException(
                    "No hay clave configurada para Google AI Studio. Define GOOGLE_AI_STUDIO_API_KEY o ia.google.api-key");
        }
    }

    @Override
    protected RespuestaIA ejecutarConsulta(SolicitudIA solicitud) {
        String modelo = resolverModelo(solicitud);
        String textoRespuesta = invocarGemini(solicitud, modelo);

        Map<String, Object> metadatos = new LinkedHashMap<>();
        metadatos.put("proveedor", "google-ai-studio");
        metadatos.put("baseUrl", configuracionIA.googleAiStudioBaseUrl());
        metadatos.put("modelo", modelo);

        return RespuestaIA.completa(textoRespuesta, modelo, metadatos);
    }

    private String resolverModelo(SolicitudIA solicitud) {
        String modeloSolicitado = solicitud.modelo();
        if (modeloSolicitado != null && !modeloSolicitado.isBlank()) {
            return modeloSolicitado;
        }

        return configuracionIA.googleAiStudioModel();
    }

    private String invocarGemini(SolicitudIA solicitud, String modelo) {
        try {
            ObjectNode root = OBJECT_MAPPER.createObjectNode();

            if (solicitud.instruccionesSistema() != null && !solicitud.instruccionesSistema().isBlank()) {
                ObjectNode systemInstruction = root.putObject("systemInstruction");
                ArrayNode parts = systemInstruction.putArray("parts");
                parts.addObject().put("text", solicitud.instruccionesSistema());
            }

            ArrayNode contents = root.putArray("contents");
            ObjectNode content = contents.addObject();
            content.put("role", "user");
            ArrayNode parts = content.putArray("parts");
            parts.addObject().put("text", solicitud.prompt());

            ObjectNode generationConfig = root.putObject("generationConfig");
            Double temperaturaConfig = solicitud.temperatura();
            Integer maxTokensConfig = solicitud.maxTokens();
            double temperatura = temperaturaConfig == null ? 0.2d : temperaturaConfig;
            int maxTokens = maxTokensConfig == null ? 512 : maxTokensConfig;
            generationConfig.put("temperature", temperatura);
            generationConfig.put("maxOutputTokens", maxTokens);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(construirUrl(modelo)))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(root)))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Error al consultar Gemini Flash: HTTP " + response.statusCode() + " - " + response.body());
            }

            return extraerTextoRespuesta(response.body());
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("No se pudo consultar Gemini Flash", ex);
        }
    }

    private String construirUrl(String modelo) {
        return configuracionIA.googleAiStudioBaseUrl()
                + "/models/"
                + modelo
                + ":generateContent?key="
                + configuracionIA.googleAiStudioApiKey();
    }

    private String extraerTextoRespuesta(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new IllegalStateException("Gemini no devolvio candidatos");
            }

            JsonNode content = candidates.get(0).path("content");
            JsonNode parts = content.path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new IllegalStateException("Gemini no devolvio contenido util");
            }

            String texto = parts.get(0).path("text").asText("");
            if (texto.isBlank()) {
                throw new IllegalStateException("Gemini devolvio una respuesta vacia");
            }

            return texto.trim();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo leer la respuesta de Gemini", ex);
        }
    }
}