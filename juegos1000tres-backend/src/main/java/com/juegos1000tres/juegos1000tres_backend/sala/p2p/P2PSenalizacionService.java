package com.juegos1000tres.juegos1000tres_backend.sala.p2p;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Service;

@Service
public class P2PSenalizacionService {

    private final Map<String, Map<String, Queue<P2PSenalizacionMensaje>>> colasPorSala = new ConcurrentHashMap<>();

    public void enviar(String salaId, P2PSenalizacionMensaje mensaje) {
        if (salaId == null || salaId.trim().isEmpty()) {
            throw new IllegalArgumentException("La sala es obligatoria");
        }
        if (mensaje == null) {
            throw new IllegalArgumentException("El mensaje de señalizacion es obligatorio");
        }

        String destinoId = limpiar(mensaje.destinoId());
        if (destinoId.isEmpty()) {
            throw new IllegalArgumentException("El destinoId es obligatorio");
        }

        colasPorSala
                .computeIfAbsent(salaId.trim(), clave -> new ConcurrentHashMap<>())
                .computeIfAbsent(destinoId, clave -> new ConcurrentLinkedQueue<>())
                .offer(mensaje);
    }

    /**
     * Devuelve TODOS los mensajes pendientes para un peer en una sola llamada.
     * Retorna lista vacía si no hay mensajes (nunca null).
     * El cliente puede así drenar la cola completa sin múltiples peticiones HTTP.
     */
    public List<P2PSenalizacionMensaje> recibirTodos(String salaId, String peerId) {
        if (salaId == null || salaId.trim().isEmpty()) {
            throw new IllegalArgumentException("La sala es obligatoria");
        }
        if (peerId == null || peerId.trim().isEmpty()) {
            throw new IllegalArgumentException("El peerId es obligatorio");
        }

        Map<String, Queue<P2PSenalizacionMensaje>> colasSala = colasPorSala.get(salaId.trim());
        if (colasSala == null) {
            return List.of();
        }

        Queue<P2PSenalizacionMensaje> cola = colasSala.get(peerId.trim());
        if (cola == null || cola.isEmpty()) {
            return List.of();
        }

        List<P2PSenalizacionMensaje> resultado = new ArrayList<>();
        P2PSenalizacionMensaje mensaje;
        while ((mensaje = cola.poll()) != null) {
            resultado.add(mensaje);
        }
        return resultado;
    }

    public void limpiarSala(String salaId) {
        if (salaId == null || salaId.trim().isEmpty()) {
            return;
        }
        colasPorSala.remove(salaId.trim());
    }

    private String limpiar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}