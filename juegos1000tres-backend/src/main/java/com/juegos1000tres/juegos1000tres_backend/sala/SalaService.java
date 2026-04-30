package com.juegos1000tres.juegos1000tres_backend.sala;

import com.juegos1000tres.juegos1000tres_backend.modelos.Jugador;
import com.juegos1000tres.juegos1000tres_backend.modelos.Pantalla;
import com.juegos1000tres.juegos1000tres_backend.modelos.Sala;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SalaService {

    private final Map<String, SalaRoom> salas = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public SalaRespuesta crearSala() {
        String uuid = generarIdUnico();
        Jugador host = new Jugador("Host");
        Sala sala = new Sala(host, new Pantalla("Lobby"));
        SalaRoom room = new SalaRoom(uuid, sala, host.getId().toString());

        salas.put(uuid, room);

        return construirRespuesta(room, host.getId().toString());
    }

    public SalaRespuesta unirse(String uuid, String nombre) {
        SalaRoom room = obtenerSala(uuid);
        Jugador jugador = room.agregarJugador(nombre);

        return construirRespuesta(room, jugador.getId().toString());
    }

    public SalaRespuesta estado(String uuid) {
        SalaRoom room = obtenerSala(uuid);

        return construirRespuesta(room, null);
    }

    public SalaRespuesta cambiarPantalla(String uuid, String actorId, String jugadorId) {
        SalaRoom room = obtenerSala(uuid);
        room.cambiarPantalla(actorId, jugadorId);

        return construirRespuesta(room, null);
    }

    public SalaRespuesta cambiarJuego(String uuid, String actorId, String juego) {
        SalaRoom room = obtenerSala(uuid);
        room.cambiarJuego(actorId, juego);

        return construirRespuesta(room, null);
    }

    public void finalizarJuego(String uuid, String actorId) {
        SalaRoom room = obtenerSala(uuid);
        room.finalizarJuego(actorId);
    }

    public void incrementarVictoria(String uuid, String jugadorId) {
        SalaRoom room = obtenerSala(uuid);
        room.sumarVictoria(jugadorId);
    }

    public SalaRoom obtenerSalaRoom(String uuid) {
        return obtenerSala(uuid);
    }

    public void salir(String uuid, String jugadorId) {
        SalaRoom room = obtenerSala(uuid);

        if (room.esCreador(jugadorId)) {
            salas.remove(uuid);
            return;
        }

        room.eliminarJugador(jugadorId);

        if (!room.isAbierta()) {
            salas.remove(uuid);
        }
    }

    public void apagar(String uuid) {
        salas.remove(uuid);
    }

    private SalaRespuesta construirRespuesta(SalaRoom room, String jugadorId) {
        List<JugadorRespuesta> jugadores = room.getJugadores().stream()
            .map(jugador -> new JugadorRespuesta(
                jugador.getId().toString(),
                jugador.getNombre(),
                jugador.getPuntuacion()
            ))
                .toList();

        return new SalaRespuesta(
                room.getUuid(),
                jugadores,
                room.getHostId(),
                room.getPantallaId(),
            room.getJuegoActual(),
            jugadorId
        );
    }

    private SalaRoom obtenerSala(String uuid) {
        SalaRoom room = salas.get(uuid);

        if (room == null) {
            throw new SalaNoEncontradaException();
        }

        return room;
    }

    private String generarIdUnico() {
        for (int intentos = 0; intentos < 20; intentos++) {
            String candidato = formatearId(100000 + random.nextInt(900000));

            if (!salas.containsKey(candidato)) {
                return candidato;
            }
        }

        String candidato = formatearId((int) (System.currentTimeMillis() % 1_000_000));

        if (!salas.containsKey(candidato)) {
            return candidato;
        }

        return formatearId((int) (System.nanoTime() % 1_000_000));
    }

    private String formatearId(int valor) {
        return String.format("%06d", Math.abs(valor));
    }
}
