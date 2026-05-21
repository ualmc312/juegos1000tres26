package com.juegos1000tres.juegos1000tres_backend.juegos.taptap;

import java.util.Objects;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.ContextoEvento;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Evento;

public class TapTapFinalizarEvento implements Evento<String> {

    private final TapTapService tapTapService;
    private final String salaId;

    public TapTapFinalizarEvento(TapTapService tapTapService, String salaId) {
        this.tapTapService = Objects.requireNonNull(tapTapService, "El servicio de TapTap es obligatorio");
        this.salaId = Objects.requireNonNull(salaId, "El ID de sala es obligatorio");
    }

    @Override
    public void hacer(String payload, ContextoEvento contexto) {
        if (payload == null || payload.isBlank()) {
            return;
        }

        try {
            String actorId = extractField(payload, "actorId");
            this.tapTapService.finalizar(this.salaId, actorId);
            // Enviar el estado actualizado
            TapTapEstadoRespuesta estado = this.tapTapService.obtenerEstado(this.salaId);
            contexto.enviar(new TapTapEstadoEnviable(estado.toMap()));
        } catch (Exception e) {
            // Log y continuar silenciosamente
            System.err.println("Error al finalizar TapTap: " + e.getMessage());
        }
    }

    private String extractField(String json, String field) {
        try {
            String pattern = "\"" + field + "\"\s*:\s*\"([^\"]*)\"";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
