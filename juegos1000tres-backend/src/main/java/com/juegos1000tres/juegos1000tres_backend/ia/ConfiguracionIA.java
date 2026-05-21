package com.juegos1000tres.juegos1000tres_backend.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfiguracionIA {

    private static final String ENV_GOOGLE_AI_STUDIO_API_KEY = "GOOGLE_AI_STUDIO_API_KEY";
    private static final String ENV_GEMINI_API_KEY = "GEMINI_API_KEY";
    private static final String ENV_GROQ_API_KEY = "GROQ_API_KEY";

    private final String googleAiStudioApiKey;
    private final String googleAiStudioModel;
    private final String googleAiStudioBaseUrl;
    private final String groqApiKey;
    private final String groqBaseUrl;

    public ConfiguracionIA(
            @Value("${ia.google.api-key:}") String googleAiStudioApiKey,
            @Value("${ia.google.model:gemini-2.0-flash}") String googleAiStudioModel,
            @Value("${ia.google.base-url:https://generativelanguage.googleapis.com/v1beta}") String googleAiStudioBaseUrl) {
        this.googleAiStudioApiKey = resolverClaveApi(googleAiStudioApiKey);
        this.googleAiStudioModel = normalizarTextoObligatorio(googleAiStudioModel, "El modelo de IA es obligatorio");
        this.googleAiStudioBaseUrl = normalizarTextoObligatorio(googleAiStudioBaseUrl, "La base URL de IA es obligatoria");
        this.groqApiKey = normalizarTextoOpcional(System.getenv(ENV_GROQ_API_KEY));
        this.groqBaseUrl = "https://api.groq.com/openai/v1";
    }

    public String googleAiStudioApiKey() {
        return googleAiStudioApiKey;
    }

    public String googleAiStudioModel() {
        return googleAiStudioModel;
    }

    public String googleAiStudioBaseUrl() {
        return googleAiStudioBaseUrl;
    }

    public String groqApiKey() {
        return groqApiKey;
    }

    public String groqBaseUrl() {
        return groqBaseUrl;
    }

    public boolean tieneClaveGoogleAiStudio() {
        return googleAiStudioApiKey != null && !googleAiStudioApiKey.isBlank();
    }

    public boolean tieneClaveGroq() {
        return groqApiKey != null && !groqApiKey.isBlank();
    }

    private static String resolverClaveApi(String claveProperty) {
        String claveNormalizada = normalizarTextoOpcional(claveProperty);
        if (claveNormalizada != null) {
            return claveNormalizada;
        }

        claveNormalizada = normalizarTextoOpcional(System.getenv(ENV_GOOGLE_AI_STUDIO_API_KEY));
        if (claveNormalizada != null) {
            return claveNormalizada;
        }

        return normalizarTextoOpcional(System.getenv(ENV_GEMINI_API_KEY));
    }

    private static String normalizarTextoObligatorio(String valor, String mensajeError) {
        String valorNormalizado = normalizarTextoOpcional(valor);
        if (valorNormalizado == null) {
            throw new IllegalStateException(mensajeError);
        }

        return valorNormalizado;
    }

    private static String normalizarTextoOpcional(String valor) {
        if (valor == null) {
            return null;
        }

        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }
}