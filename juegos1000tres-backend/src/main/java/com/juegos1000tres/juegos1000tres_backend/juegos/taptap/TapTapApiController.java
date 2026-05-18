package com.juegos1000tres.juegos1000tres_backend.juegos.taptap;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juegos1000tres.juegos1000tres_backend.sala.JuegoManager;

/**
 * Adapter HTTP para que TapTap funcione con la arquitectura ApiConexion/Traductor.
 * Expone POST /api/salas/{salaId}/taptap/eventos y GET /actualizaciones
 */
@RestController
@RequestMapping("/api/salas/{salaId}/taptap")
@CrossOrigin(origins = "*")
public class TapTapApiController {

    private final TapTapService tapTapService;
    private final JuegoManager juegoManager;

    public TapTapApiController(TapTapService tapTapService, JuegoManager juegoManager) {
        this.tapTapService = tapTapService;
        this.juegoManager = juegoManager;
    }

    @PostMapping("/eventos")
    public ResponseEntity<?> handleEvento(@PathVariable String salaId, @RequestBody String payload) {
        try {
            if (payload == null || payload.isBlank()) {
                return ResponseEntity.badRequest().body("El payload es obligatorio");
            }

            // Delegar en JuegoManager para que la comunicación pase por Traductor/Envio/Recibo
            Object result = juegoManager.procesarMensaje(salaId, "taptap", payload);
            return ResponseEntity.ok(result);
        } catch (NoSuchMethodError | NullPointerException e) {
            // Si JuegoManager no expone el método, fallback al servicio directo
            try {
                String comando = extractStringField(payload, "comando");
                if ("REGISTRAR_PUNTO".equalsIgnoreCase(comando)) {
                    String jugadorId = extractStringField(payload, "jugadorId");
                    TapTapPuntoRespuesta resp = tapTapService.registrarPunto(salaId, jugadorId);
                    return ResponseEntity.ok(resp);
                }
                if ("FINALIZAR".equalsIgnoreCase(comando)) {
                    String actorId = extractStringField(payload, "actorId");
                    TapTapFinalRespuesta resp = tapTapService.finalizar(salaId, actorId);
                    return ResponseEntity.ok(resp);
                }
                return ResponseEntity.accepted().body(Map.of("status", "accepted"));
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", ex.getMessage()));
            }
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/actualizaciones")
    public ResponseEntity<?> obtenerActualizaciones(@PathVariable String salaId) {
        try {
            Object estado = juegoManager.obtenerEstado(salaId, "taptap");
            if (estado == null) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(estado);
        } catch (NoSuchMethodError | NullPointerException e) {
            try {
                TapTapEstadoRespuesta estado = tapTapService.obtenerEstado(salaId);
                if (estado == null) {
                    return ResponseEntity.noContent().build();
                }
                return ResponseEntity.ok(estado);
            } catch (Exception ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", ex.getMessage()));
            }
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    private String extractStringField(String json, String field) {
        try {
            // muy simple: buscar "field"\s*:\s*"value"
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
