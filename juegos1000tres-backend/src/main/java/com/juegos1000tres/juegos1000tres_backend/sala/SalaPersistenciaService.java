package com.juegos1000tres.juegos1000tres_backend.sala;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juegos1000tres.juegos1000tres_backend.modelos.JuegoEntities;
import com.juegos1000tres.juegos1000tres_backend.modelos.Jugador;
import com.juegos1000tres.juegos1000tres_backend.modelos.SalaEntities;
import com.juegos1000tres.juegos1000tres_backend.modelos.SalaJuegoOrden;
import com.juegos1000tres.juegos1000tres_backend.modelos.SalaJuegoResultado;
import com.juegos1000tres.juegos1000tres_backend.repositorios.JuegoEntitiesRepository;
import com.juegos1000tres.juegos1000tres_backend.repositorios.SalaEntitiesRepository;
import com.juegos1000tres.juegos1000tres_backend.repositorios.SalaJuegoOrdenRepository;

@Service
public class SalaPersistenciaService {

    private final SalaEntitiesRepository salaEntitiesRepository;
    private final SalaJuegoOrdenRepository salaJuegoOrdenRepository;
    private final JuegoEntitiesRepository juegoEntitiesRepository;

    public SalaPersistenciaService(
            SalaEntitiesRepository salaEntitiesRepository,
            SalaJuegoOrdenRepository salaJuegoOrdenRepository,
            JuegoEntitiesRepository juegoEntitiesRepository) {
        this.salaEntitiesRepository = salaEntitiesRepository;
        this.salaJuegoOrdenRepository = salaJuegoOrdenRepository;
        this.juegoEntitiesRepository = juegoEntitiesRepository;
    }

    @Transactional
    public SalaEntities registrarSalaCreada(String salaUuid, String hostNombre, String hostUsuarioId) {
        return salaEntitiesRepository.findByUuid(salaUuid)
                .orElseGet(() -> {
                    SalaEntities sala = new SalaEntities(salaUuid, normalizarNombre(hostNombre), LocalDate.now());
                    aplicarHostIdentidad(sala, hostUsuarioId, salaUuid);
                    return salaEntitiesRepository.save(sala);
                });
    }

    @Transactional
    public SalaJuegoOrden registrarJuegoIniciado(String salaUuid, String juegoNombre, List<Jugador> jugadores) {
        SalaEntities sala = obtenerSala(salaUuid);
        JuegoEntities juego = obtenerOCrearJuego(juegoNombre, jugadores == null ? 0 : jugadores.size());
        int orden = salaJuegoOrdenRepository.findFirstBySalaUuidOrderByOrdenDesc(salaUuid)
                .map(SalaJuegoOrden::getOrden)
                .orElse(0) + 1;

        SalaJuegoOrden juegoOrden = new SalaJuegoOrden(sala, juego, orden);
        juegoOrden.setFechaJugado(LocalDate.now());
        sala.registrarJuego(juegoOrden);
        salaEntitiesRepository.save(sala);
        return juegoOrden;
    }

    @Transactional
    public void registrarResultadosJuego(String salaUuid, List<Jugador> jugadores) {
        Optional<SalaJuegoOrden> juegoActual = salaJuegoOrdenRepository.findFirstBySalaUuidOrderByOrdenDesc(salaUuid);

        if (juegoActual.isEmpty()) {
            return;
        }

        SalaJuegoOrden juego = juegoActual.get();
        juego.limpiarJugadores();
        salaJuegoOrdenRepository.saveAndFlush(juego);

        List<Jugador> jugadoresOrdenados = (jugadores == null ? List.<Jugador>of() : jugadores).stream()
                .sorted(Comparator.comparingInt(Jugador::getPuntuacion).reversed()
                        .thenComparing(Jugador::getNombre, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int mejorPuntuacion = jugadoresOrdenados.stream()
                .mapToInt(Jugador::getPuntuacion)
                .max()
                .orElse(0);

        int posicion = 1;
        for (Jugador jugador : jugadoresOrdenados) {
            SalaJuegoResultado resultado = new SalaJuegoResultado(
                    juego,
                    jugador.getNombre(),
                    parsearUsuarioId(jugador.getUsuarioId()),
                    jugador.getPuntuacion(),
                    mejorPuntuacion > 0 && jugador.getPuntuacion() == mejorPuntuacion);
            resultado.setPosicion(posicion++);
            juego.registrarJugador(resultado);
        }

        salaJuegoOrdenRepository.save(juego);
    }

    @Transactional(readOnly = true)
    public Optional<SalaJuegoOrden> obtenerUltimoJuego(String salaUuid) {
        return salaJuegoOrdenRepository.findFirstBySalaUuidOrderByOrdenDesc(salaUuid);
    }

    private SalaEntities obtenerSala(String salaUuid) {
        return salaEntitiesRepository.findByUuid(salaUuid)
                .orElseThrow(() -> new IllegalArgumentException("La sala no existe en la persistencia"));
    }

    private JuegoEntities obtenerOCrearJuego(String juegoNombre, int numeroJugadores) {
        String nombreLimpio = normalizarNombre(juegoNombre);

        return juegoEntitiesRepository.findByNombreIgnoreCase(nombreLimpio)
                .orElseGet(() -> juegoEntitiesRepository.save(new JuegoEntities(
                        Math.max(0, numeroJugadores),
                        nombreLimpio,
                        "Juego persistido desde la sala",
                        nombreLimpio.toLowerCase())));
    }

    private String normalizarNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            return "Desconocido";
        }

        return valor.trim();
    }

    private String normalizarIdentidad(String valor, String fallback) {
        if (valor != null && !valor.isBlank()) {
            return valor.trim();
        }

        return fallback == null || fallback.isBlank() ? "desconocido" : fallback.trim();
    }

    private void aplicarHostIdentidad(SalaEntities sala, String hostUsuarioId, String fallback) {
        String identidad = normalizarIdentidad(hostUsuarioId, fallback);

        try {
            sala.setHostUsuarioId(Long.valueOf(identidad));
            sala.setHostUsuarioToken(null);
        } catch (NumberFormatException ex) {
            sala.setHostUsuarioId(null);
            sala.setHostUsuarioToken(identidad);
        }
    }

    private Long parsearUsuarioId(String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(usuarioId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}