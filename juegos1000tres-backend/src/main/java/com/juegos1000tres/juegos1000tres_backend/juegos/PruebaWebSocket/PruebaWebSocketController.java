package com.juegos1000tres.juegos1000tres_backend.juegos.PruebaWebSocket;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/pruebas/websocket-chat")
public class PruebaWebSocketController {

    private final PruebaWebSocketService service;

    public PruebaWebSocketController(PruebaWebSocketService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        return ResponseEntity.ok(this.service.obtenerConfig());
    }

    @GetMapping("/mensajes")
    public ResponseEntity<?> getMensajes() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mensajes", this.service.obtenerMensajesGlobales());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/pantalla")
    public ResponseEntity<?> getEstadoPantalla() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jugadores", this.service.obtenerEstadoPantalla());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/estado")
    public ResponseEntity<?> getEstado() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enCurso", this.service.isJuegoEnCurso());
        return ResponseEntity.ok(body);
    }
}