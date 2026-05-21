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

    private static final long RETARDO_CIERRE_MS = 3_000L;

    private final Map<String, HandicapPartida> partidas = new ConcurrentHashMap<>();
    private final SalaService salaService;

    public HandicapService(SalaService salaService) {
        this.salaService = salaService;
    }

    public HandicapEstadoRespuesta obtenerEstado(String uuid) {
        SalaRoom sala = salaService.obtenerSalaRoom(uuid);
        HandicapPartida partida = partidas.computeIfAbsent(uuid, key -> new HandicapPartida());

        if (!esJuegoActivo(sala) && !partida.isFinalizada()) {
            throw new IllegalArgumentException("Juego no activo");
        }

        long ahora = System.currentTimeMillis();
        if (partida.debeFinalizar(ahora)) {
            aplicarResultados(uuid, sala, partida);
        }

        return construirRespuesta(sala, partida, ahora);
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
            partida.confirmar(ganadoresValidados, System.currentTimeMillis());
        }

        return construirRespuesta(sala, partida, System.currentTimeMillis());
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

    private void aplicarResultados(String uuid, SalaRoom sala, HandicapPartida partida) {
        if (partida.isResultadosAplicados()) {
            return;
        }

        for (String ganadorId : partida.getGanadores()) {
            salaService.incrementarVictoria(uuid, ganadorId);
        }

        partida.marcarResultadosAplicados();
        partida.marcarFinalizada();

        String hostId = sala.getHostId();
        if (hostId != null && !hostId.isBlank()) {
            salaService.finalizarJuego(uuid, hostId);
        }
    }

    private HandicapEstadoRespuesta construirRespuesta(SalaRoom sala, HandicapPartida partida, long ahora) {
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

        long restanteMs = partida.isConfirmada() && !partida.isFinalizada()
                ? partida.getTiempoRestanteMs(ahora)
                : 0L;

        return new HandicapEstadoRespuesta(
                fase,
                jugadores,
                ganadores,
                restanteMs,
                partida.isConfirmada());
    }

    private boolean esJuegoActivo(SalaRoom sala) {
        return "handicap".equalsIgnoreCase(sala.getJuegoActual());
    }

    private static final class HandicapPartida {
        private List<String> ganadores = new ArrayList<>();
        private Long confirmacionEpochMs;
        private boolean resultadosAplicados;
        private boolean finalizada;

        boolean isConfirmada() {
            return confirmacionEpochMs != null;
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

        void confirmar(List<String> ganadores, long ahora) {
            if (confirmacionEpochMs != null) {
                return;
            }
            this.ganadores = ganadores == null ? List.of() : List.copyOf(ganadores);
            this.confirmacionEpochMs = ahora;
        }

        long getTiempoRestanteMs(long ahora) {
            if (confirmacionEpochMs == null) {
                return 0L;
            }
            long restante = RETARDO_CIERRE_MS - (ahora - confirmacionEpochMs);
            return Math.max(restante, 0L);
        }

        boolean debeFinalizar(long ahora) {
            return confirmacionEpochMs != null
                    && !finalizada
                    && (ahora - confirmacionEpochMs) >= RETARDO_CIERRE_MS;
        }

        void marcarResultadosAplicados() {
            this.resultadosAplicados = true;
        }

        void marcarFinalizada() {
            this.finalizada = true;
        }
    }
}
