package com.juegos1000tres.juegos1000tres_backend.juegos.handicap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.juegos1000tres.juegos1000tres_backend.modelos.Jugador;
import com.juegos1000tres.juegos1000tres_backend.sala.JugadorRespuesta;
import com.juegos1000tres.juegos1000tres_backend.sala.SalaRoom;
import com.juegos1000tres.juegos1000tres_backend.sala.SalaService;

@Service
public class HandicapService {

    private final Map<String, HandicapPartida> partidas = new ConcurrentHashMap<>();
    private final SalaService salaService;

    public HandicapService(SalaService salaService) {
        this.salaService = salaService;
    }

    public HandicapEstadoRespuesta obtenerEstado(String uuid) {
        SalaRoom sala = salaService.obtenerSalaRoom(uuid);
        HandicapPartida partida = partidas.computeIfAbsent(uuid, key -> new HandicapPartida());
        if (esJuegoActivo(sala) && partida.isFinalizada()) {
            partida = new HandicapPartida();
            partidas.put(uuid, partida);
        }

        if (!esJuegoActivo(sala) && !partida.isFinalizada()) {
            throw new IllegalArgumentException("Juego no activo");
        }

        return construirRespuesta(sala, partida);
    }

    public HandicapEstadoRespuesta confirmarSeleccion(String uuid, String actorId, List<String> ganadores) {
        SalaRoom sala = salaService.obtenerSalaRoom(uuid);
        if (!esJuegoActivo(sala)) {
            throw new IllegalArgumentException("Juego no activo");
        }

        if (!sala.getHostId().equals(actorId)) {
            throw new SecurityException("Solo el host puede confirmar");
        }

        HandicapPartida partida = partidas.computeIfAbsent(uuid, key -> new HandicapPartida());
        if (!partida.isConfirmada()) {
            List<String> ganadoresValidados = filtrarGanadores(sala, ganadores);
            partida.confirmar(ganadoresValidados);
            aplicarResultados(uuid, partida);
        }

        return construirRespuesta(sala, partida);
    }

    public void limpiarPartida(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return;
        }

        partidas.remove(uuid);
    }

    private List<String> filtrarGanadores(SalaRoom sala, List<String> ganadores) {
        if (ganadores == null || ganadores.isEmpty()) {
            return List.of();
        }

        Set<String> jugadoresSala = new LinkedHashSet<>();
        for (Jugador jugador : sala.getJugadores()) {
            jugadoresSala.add(jugador.getId().toString());
        }

        Set<String> resultado = new LinkedHashSet<>();
        for (String candidato : ganadores) {
            if (candidato != null && jugadoresSala.contains(candidato)) {
                resultado.add(candidato);
            }
        }

        return new ArrayList<>(resultado);
    }

    private void aplicarResultados(String uuid, HandicapPartida partida) {
        if (partida.isResultadosAplicados()) {
            return;
        }

        for (String ganadorId : partida.getGanadores()) {
            salaService.incrementarVictoria(uuid, ganadorId);
        }

        partida.marcarResultadosAplicados();
    }

    private HandicapEstadoRespuesta construirRespuesta(SalaRoom sala, HandicapPartida partida) {
        List<JugadorRespuesta> jugadores = sala.getJugadores().stream()
                .map(jugador -> new JugadorRespuesta(
                        jugador.getId().toString(),
                        jugador.getNombre(),
                        jugador.getPuntuacion()))
                .toList();

        List<JugadorRespuesta> ganadores = partida.getGanadores().stream()
                .map(id -> jugadores.stream()
                        .filter(jugador -> jugador.id().equals(id))
                        .findFirst()
                        .orElse(null))
                .filter(item -> item != null)
                .toList();

        String fase = partida.isFinalizada()
                ? "FINALIZADO"
                : partida.isConfirmada() ? "MOSTRANDO_RESULTADO" : "SELECCIONANDO";

        return new HandicapEstadoRespuesta(
                fase,
                jugadores,
                ganadores,
                0L,
                partida.isConfirmada());
    }

    private boolean esJuegoActivo(SalaRoom sala) {
        return "handicap".equalsIgnoreCase(sala.getJuegoActual());
    }

    private static final class HandicapPartida {
        private List<String> ganadores = new ArrayList<>();
        private boolean confirmada;
        private boolean resultadosAplicados;
        private boolean finalizada;

        boolean isConfirmada() {
            return confirmada;
        }

        boolean isResultadosAplicados() {
            return resultadosAplicados;
        }

        boolean isFinalizada() {
            return finalizada;
        }

        List<String> getGanadores() {
            return ganadores;
        }

        void confirmar(List<String> ganadores) {
            if (confirmada) {
                return;
            }
            this.ganadores = ganadores == null ? List.of() : List.copyOf(ganadores);
            this.confirmada = true;
        }

        void marcarResultadosAplicados() {
            this.resultadosAplicados = true;
        }

        void marcarFinalizada() {
            this.finalizada = true;
        }
    }
}
