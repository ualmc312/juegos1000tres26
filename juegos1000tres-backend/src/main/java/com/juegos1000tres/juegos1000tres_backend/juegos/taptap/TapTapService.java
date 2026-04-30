package com.juegos1000tres.juegos1000tres_backend.juegos.taptap;

import com.juegos1000tres.juegos1000tres_backend.modelos.Jugador;
import com.juegos1000tres.juegos1000tres_backend.sala.SalaRoom;
import com.juegos1000tres.juegos1000tres_backend.sala.SalaService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TapTapService {

    private static final long DURACION_MS = 60_000L;
    private static final long RETRASO_INICIO_MS = 1_500L;

    private final Map<String, TapTapPartida> partidas = new ConcurrentHashMap<>();
    private final SalaService salaService;

    public TapTapService(SalaService salaService) {
        this.salaService = salaService;
    }

    public TapTapEstadoRespuesta obtenerEstado(String uuid) {
        SalaRoom sala = salaService.obtenerSalaRoom(uuid);
        validarJuegoActivo(sala);

        TapTapPartida partida = partidas.compute(uuid, (key, actual) -> {
            if (actual == null || actual.estaFinalizada()) {
                return crearPartida();
            }
            return actual;
        });

        return construirEstado(sala, partida);
    }

    public TapTapPuntoRespuesta registrarPunto(String uuid, String jugadorId) {
        SalaRoom sala = salaService.obtenerSalaRoom(uuid);
        validarJuegoActivo(sala);

        TapTapPartida partida = obtenerPartida(uuid);
        long ahora = System.currentTimeMillis();
        int puntos = partida.sumarPunto(jugadorId, ahora);

        return new TapTapPuntoRespuesta(puntos);
    }

    public TapTapFinalRespuesta finalizar(String uuid, String actorId) {
        SalaRoom sala = salaService.obtenerSalaRoom(uuid);
        validarJuegoActivo(sala);

        TapTapPartida partida = obtenerPartida(uuid);
        long ahora = System.currentTimeMillis();

        if (!partida.estaFinalizada() && partida.getRestanteMs(ahora) > 0) {
            return new TapTapFinalRespuesta(null, false);
        }

        if (!partida.estaFinalizada()) {
            List<String> ganadores = resolverGanadores(sala, partida);
            boolean victoriaRegistrada = false;

            if (!ganadores.isEmpty()) {
                ganadores.forEach(ganadorId -> salaService.incrementarVictoria(uuid, ganadorId));
                victoriaRegistrada = true;
            }

            salaService.finalizarJuego(uuid, actorId);
            partida.finalizar(ganadores.size() == 1 ? ganadores.get(0) : null);

            return new TapTapFinalRespuesta(ganadores.size() == 1 ? ganadores.get(0) : null, victoriaRegistrada);
        }

        return new TapTapFinalRespuesta(partida.getGanadorId(), false);
    }

    private TapTapPartida obtenerPartida(String uuid) {
        return partidas.compute(uuid, (key, actual) -> actual == null ? crearPartida() : actual);
    }

    private TapTapPartida crearPartida() {
        long inicio = System.currentTimeMillis() + RETRASO_INICIO_MS;
        return new TapTapPartida(inicio, DURACION_MS);
    }

    private TapTapEstadoRespuesta construirEstado(SalaRoom sala, TapTapPartida partida) {
        long ahora = System.currentTimeMillis();
        String pantallaId = sala.getPantallaId();
        List<TapTapPuntuacion> puntuaciones = sala.getJugadores().stream()
            .filter(jugador -> pantallaId == null
                || pantallaId.isBlank()
                || SalaRoom.PANTALLA_NINGUNO.equals(pantallaId)
                || !jugador.getId().toString().equals(pantallaId))
            .map(jugador -> new TapTapPuntuacion(
                jugador.getId().toString(),
                jugador.getNombre(),
                partida.obtenerPuntos(jugador.getId().toString())
            ))
            .toList();

        return new TapTapEstadoRespuesta(
                partida.getInicioEpochMs(),
                partida.getDuracionMs(),
                partida.getRestanteMs(ahora),
                partida.estaFinalizada(),
                partida.getGanadorId(),
                puntuaciones
        );
    }

    private List<String> resolverGanadores(SalaRoom sala, TapTapPartida partida) {
        List<Jugador> jugadores = sala.getJugadores();
        if (jugadores.isEmpty()) {
            return List.of();
        }

        String pantallaId = sala.getPantallaId();
        List<Jugador> jugadoresElegibles = jugadores.stream()
                .filter(jugador -> pantallaId == null
                        || pantallaId.isBlank()
                        || SalaRoom.PANTALLA_NINGUNO.equals(pantallaId)
                        || !jugador.getId().toString().equals(pantallaId))
                .toList();

        if (jugadoresElegibles.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> puntos = partida.getPuntos();
        int maxPuntos = jugadoresElegibles.stream()
                .mapToInt(jugador -> puntos.getOrDefault(jugador.getId().toString(), 0))
                .max()
                .orElse(0);

        if (maxPuntos <= 0) {
            return List.of();
        }

        return jugadoresElegibles.stream()
                .filter(jugador -> puntos.getOrDefault(jugador.getId().toString(), 0) == maxPuntos)
                .map(jugador -> jugador.getId().toString())
                .toList();
    }

    private void validarJuegoActivo(SalaRoom sala) {
        if (!"taptap".equalsIgnoreCase(sala.getJuegoActual())) {
            throw new IllegalArgumentException("Juego no activo");
        }
    }
}
