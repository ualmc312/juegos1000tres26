package com.juegos1000tres.juegos1000tres_backend.modelos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class Sala {

    private final UUID id;
    private final List<Jugador> jugadores;
    private Jugador host;
    private Pantalla pantalla;
    private boolean abierta;

    public Sala(Jugador host, Pantalla pantalla) {
        this(UUID.randomUUID(), host, pantalla);
    }

    public Sala(UUID id, Jugador host, Pantalla pantalla) {
        this.id = Objects.requireNonNull(id, "El id de la sala es obligatorio");
        this.host = Objects.requireNonNull(host, "La sala requiere un host");
        this.pantalla = Objects.requireNonNull(pantalla, "La sala requiere una pantalla");
        this.jugadores = new ArrayList<>();
        this.jugadores.add(host);
        this.abierta = true;

        sincronizarPantalla("Sala creada");
    }

    public UUID getId() {
        return id;
    }

    public List<Jugador> getJugadores() {
        return Collections.unmodifiableList(jugadores);
    }

    public Jugador getHost() {
        return host;
    }

    public Pantalla getPantalla() {
        return pantalla;
    }

    public boolean isAbierta() {
        return abierta;
    }

    public void agregarJugador(Jugador jugador) {
        validarSalaAbierta();
        Objects.requireNonNull(jugador, "El jugador es obligatorio");

        if (contieneJugador(jugador.getId())) {
            throw new IllegalArgumentException("El jugador ya se encuentra en la sala");
        }

        jugadores.add(jugador);
        sincronizarPantalla("Jugador unido");
    }

    public void eliminarJugador(UUID jugadorId) {
        validarSalaAbierta();
        Objects.requireNonNull(jugadorId, "El id del jugador es obligatorio");

        Jugador jugador = buscarJugador(jugadorId)
                .orElseThrow(() -> new IllegalArgumentException("No existe un jugador con ese id"));

        jugadores.remove(jugador);

        if (host.getId().equals(jugadorId)) {
            host = jugadores.isEmpty() ? null : jugadores.get(0);
        }

        if (jugadores.isEmpty()) {
            cerrarSala();
            return;
        }

        sincronizarPantalla("Jugador eliminado");
    }

    public void cambiarHost(UUID nuevoHostId) {
        validarSalaAbierta();
        Objects.requireNonNull(nuevoHostId, "El id del host es obligatorio");

        this.host = buscarJugador(nuevoHostId)
                .orElseThrow(() -> new IllegalArgumentException("El nuevo host debe pertenecer a la sala"));

        sincronizarPantalla("Host actualizado");
    }

    public void setPantalla(Pantalla pantalla) {
        this.pantalla = Objects.requireNonNull(pantalla, "La pantalla es obligatoria");
        sincronizarPantalla("Pantalla actualizada");
    }

    public void cerrarSala() {
        this.abierta = false;
        pantalla.actualizarEstadoPartida("Sala cerrada");
        pantalla.actualizarJugadoresConectados(contarJugadoresConectados());
    }

    private Optional<Jugador> buscarJugador(UUID jugadorId) {
        return jugadores.stream()
                .filter(jugador -> jugador.getId().equals(jugadorId))
                .findFirst();
    }

    private boolean contieneJugador(UUID jugadorId) {
        return buscarJugador(jugadorId).isPresent();
    }

    private int contarJugadoresConectados() {
        return (int) jugadores.stream()
                .filter(Jugador::isConectado)
                .count();
    }

    private void validarSalaAbierta() {
        if (!abierta) {
            throw new IllegalStateException("La sala se encuentra cerrada");
        }
    }

    private void sincronizarPantalla(String estadoPartida) {
        pantalla.actualizarEstadoPartida(estadoPartida);
        pantalla.actualizarJugadoresConectados(contarJugadoresConectados());
    }
}
