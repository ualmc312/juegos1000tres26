package com.juegos1000tres.juegos1000tres_backend.sala.p2p;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/p2p/salas/{salaId}")
public class P2PController {

    private final P2PSenalizacionService p2pSenalizacionService;

    public P2PController(P2PSenalizacionService p2pSenalizacionService) {
        this.p2pSenalizacionService = p2pSenalizacionService;
    }

    @PostMapping("/mensajes")
    public ResponseEntity<Void> enviarMensaje(@PathVariable String salaId,
                                              @RequestBody P2PSenalizacionMensaje mensaje) {
        this.p2pSenalizacionService.enviar(salaId, mensaje);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Devuelve TODOS los mensajes pendientes para el peer en una sola respuesta.
     * Retorna 200 con lista vacía [] si no hay mensajes (nunca 204).
     * Esto permite al cliente drenar la cola completa en una sola petición HTTP.
     */
    @GetMapping("/mensajes")
    public ResponseEntity<List<P2PSenalizacionMensaje>> recibirMensajes(@PathVariable String salaId,
                                                                         @RequestParam String peerId) {
        List<P2PSenalizacionMensaje> mensajes = this.p2pSenalizacionService.recibirTodos(salaId, peerId);
        return ResponseEntity.ok(mensajes);
    }
}