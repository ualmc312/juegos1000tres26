package com.juegos1000tres.juegos1000tres_backend.sala;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador para eventos y actualizaciones de juegos por sala.
 * Maneja peticiones a /api/salas/{salaId}/{juego}/eventos y /actualizaciones.
 */
@RestController
@RequestMapping("/api/salas/{salaId}")
@CrossOrigin(origins = "*")
public class SalaJuegoController {

    private final JuegoManager juegoManager;

    public SalaJuegoController(JuegoManager juegoManager) {
        this.juegoManager = juegoManager;
    }

    /**
     * POST /api/salas/{salaId}/{juego}/eventos
     * Procesa un comando/evento enviado por un cliente.
     * Devuelve el estado actualizado del juego.
     */
    @PostMapping("/{juego}/eventos")
    public ResponseEntity<?> procesarEvento(
            @PathVariable String salaId,
            @PathVariable String juego,
            @RequestBody String payload) {
        try {
            if (payload == null || payload.isBlank()) {
                return ResponseEntity.badRequest().body("El payload es obligatorio");
            }

            // Procesar el evento
            juegoManager.procesarMensaje(salaId, juego, payload);
            
            // Devolver el estado actualizado
            String estado = juegoManager.obtenerEstado(salaId, juego);
            if (estado == null || estado.isBlank() || "{}".equals(estado.trim())) {
                return ResponseEntity.accepted().body("{\"status\":\"accepted\"}");
            }
            return ResponseEntity.accepted().body(estado);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
        }
    }

    /**
     * GET /api/salas/{salaId}/{juego}/actualizaciones
     * Obtiene el estado actual del juego.
     */
    @GetMapping("/{juego}/actualizaciones")
    public ResponseEntity<?> obtenerActualizaciones(
            @PathVariable String salaId,
            @PathVariable String juego) {
        try {
            String estado = juegoManager.obtenerEstado(salaId, juego);
            
            if (estado == null || estado.isBlank() || "{}".equals(estado.trim())) {
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok(estado);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"" + escapeJson(ex.getMessage()) + "\"}");
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
