package com.juegos1000tres.juegos1000tres_backend.ia.groq;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.juegos1000tres.juegos1000tres_backend.ia.ClienteApiIA;
import com.juegos1000tres.juegos1000tres_backend.ia.ConfiguracionIA;
import com.juegos1000tres.juegos1000tres_backend.ia.RespuestaIA;
import com.juegos1000tres.juegos1000tres_backend.ia.SolicitudIA;

@Service
@ConditionalOnProperty(name = "GROQ_API_KEY")
public class GroqClienteIA extends ClienteApiIA {

    private static final String MODELO_POR_DEFECTO = "llama-3.3-70b-versatile";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final ConfiguracionIA configuracionIA;

    public GroqClienteIA(ConfiguracionIA configuracionIA) {
        this.configuracionIA = configuracionIA;
    }

    @Override
    protected void validarSolicitud(SolicitudIA solicitud) {
        super.validarSolicitud(solicitud);

        if (!configuracionIA.tieneClaveGroq()) {
            throw new IllegalStateException("No hay clave configurada para Groq. Define GROQ_API_KEY en el entorno o en .env");
        }
    }

    @Override
    protected RespuestaIA ejecutarConsulta(SolicitudIA solicitud) {
        String modelo = resolverModelo(solicitud);
        String textoRespuesta = invocarGroq(solicitud, modelo);

        Map<String, Object> metadatos = new LinkedHashMap<>();
        metadatos.put("proveedor", "groq");
        metadatos.put("baseUrl", configuracionIA.groqBaseUrl());
        metadatos.put("modelo", modelo);

        return RespuestaIA.completa(textoRespuesta, modelo, metadatos);
    }

    private String resolverModelo(SolicitudIA solicitud) {
        String modeloSolicitado = solicitud.modelo();
        if (modeloSolicitado != null && !modeloSolicitado.isBlank()) {
            return modeloSolicitado;
        }

        return MODELO_POR_DEFECTO;
    }

    private String invocarGroq(SolicitudIA solicitud, String modelo) {
        try {
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            double temperatura = solicitud.temperatura() == null ? 0.2d : solicitud.temperatura().doubleValue();
            int maxCompletionTokens = solicitud.maxTokens() == null ? 512 : solicitud.maxTokens().intValue();
            root.put("model", modelo);
            root.put("temperature", temperatura);
            root.put("max_completion_tokens", maxCompletionTokens);

            ArrayNode messages = root.putArray("messages");
            if (solicitud.instruccionesSistema() != null && !solicitud.instruccionesSistema().isBlank()) {
                ObjectNode systemMessage = messages.addObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", solicitud.instruccionesSistema());
            }

            ObjectNode userMessage = messages.addObject();
            userMessage.put("role", "user");
            userMessage.put("content", solicitud.prompt());

            String endpoint = configuracionIA.groqBaseUrl() + "/chat/completions";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + configuracionIA.groqApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(root)))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Error al consultar Groq: HTTP " + response.statusCode() + " - " + response.body());
            }

            return extraerTextoRespuesta(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("No se pudo consultar Groq", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo consultar Groq", ex);
        }
    }

    private String extraerTextoRespuesta(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                String contenido = message.path("content").asText("").trim();
                if (!contenido.isBlank()) {
                    return contenido;
                }
            }

            throw new IllegalStateException("Groq no devolvio contenido util: " + body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo leer la respuesta de Groq", ex);
        }
    }
}
