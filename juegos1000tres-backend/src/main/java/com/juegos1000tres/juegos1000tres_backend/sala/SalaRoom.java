package com.juegos1000tres.juegos1000tres_backend.sala;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.juegos1000tres.juegos1000tres_backend.modelos.Jugador;
import com.juegos1000tres.juegos1000tres_backend.modelos.Sala;

public class SalaRoom {

    public static final String PANTALLA_NINGUNO = "NINGUNO";

    private final String uuid;
    private final Sala sala;
    private final String creadorId;
    private String pantallaId;
    private String juegoActual;
    private String p2pHostPeerId;
    private int contadorNombres = 1;
    private int contadorInvitados = 1;
    private final Map<String, Integer> invitadosPorUsuario = new ConcurrentHashMap<>();

    public SalaRoom(String uuid, Sala sala, String creadorId) {
        this.uuid = Objects.requireNonNull(uuid, "uuid requerido");
        this.sala = Objects.requireNonNull(sala, "sala requerida");
        this.creadorId = Objects.requireNonNull(creadorId, "creador requerido");
        this.pantallaId = creadorId;
        this.juegoActual = "";
        this.p2pHostPeerId = "";
    }

    public synchronized Jugador agregarJugador(String nombre, String usuarioId, boolean esInvitado) {
        String nombreFinal = esInvitado
                ? resolverNombreInvitado(usuarioId)
                : resolverNombreJugador(nombre);
        Jugador jugador = new Jugador(nombreFinal);
        sala.agregarJugador(jugador);
        return jugador;
    }

    public synchronized void registrarInvitado(String usuarioId) {
        String clave = (usuarioId == null || usuarioId.isBlank()) ? null : usuarioId.trim();

        if (clave != null && !invitadosPorUsuario.containsKey(clave)) {
            invitadosPorUsuario.put(clave, 1);
        }

        if (contadorInvitados <= 1) {
            contadorInvitados = 2;
        }
    }

    private String resolverNombreJugador(String nombre) {
        return (nombre == null || nombre.isBlank())
                ? "Jugador " + contadorNombres++
                : nombre.trim();
    }

    private String resolverNombreInvitado(String usuarioId) {
        String clave = (usuarioId == null || usuarioId.isBlank()) ? null : usuarioId.trim();
        int numero;

        if (clave == null) {
            numero = contadorInvitados++;
        } else {
            Integer asignado = invitadosPorUsuario.get(clave);
            if (asignado == null) {
                asignado = contadorInvitados++;
                invitadosPorUsuario.put(clave, asignado);
            }
            numero = asignado;
        }

        if (numero == 1) {
            return "invitado";
        }

        return "invitado " + numero;
    }

    public synchronized void eliminarJugador(String jugadorId) {
        UUID id = UUID.fromString(jugadorId);
        sala.eliminarJugador(id);

        if (jugadorId.equals(pantallaId)) {
            pantallaId = sala.getHost() != null ? sala.getHost().getId().toString() : "";
        }
    }

    public synchronized void cambiarPantalla(String actorId, String jugadorId) {
        if (!creadorId.equals(actorId)) {
            throw new SecurityException("Solo el creador puede cambiar la pantalla");
        }

        if (PANTALLA_NINGUNO.equals(jugadorId)) {
            pantallaId = PANTALLA_NINGUNO;
            return;
        }

        UUID id = UUID.fromString(jugadorId);
        boolean existe = sala.getJugadores().stream()
                .anyMatch(jugador -> jugador.getId().equals(id));

        if (!existe) {
            throw new IllegalArgumentException("Jugador no encontrado");
        }

        pantallaId = jugadorId;
    }

    public synchronized void cambiarJuego(String actorId, String juego) {
        if (!creadorId.equals(actorId)) {
            throw new SecurityException("Solo el creador puede cambiar el juego");
        }

        if (juego == null || juego.isBlank()) {
            throw new IllegalArgumentException("Juego invalido");
        }

        this.juegoActual = juego.trim();
        this.p2pHostPeerId = "reflejos-p2p".equalsIgnoreCase(this.juegoActual) ? UUID.randomUUID().toString() : "";
    }

    public synchronized void finalizarJuego(String actorId) {
        if (!creadorId.equals(actorId)) {
            throw new SecurityException("Solo el creador puede finalizar el juego");
        }

        this.juegoActual = "";
    }

    public synchronized void sumarVictoria(String jugadorId) {
        UUID id = UUID.fromString(jugadorId);
        Jugador jugador = sala.getJugadores().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado"));

        jugador.sumarPuntos(1);
    }

    public boolean esCreador(String jugadorId) {
        return creadorId.equals(jugadorId);
    }

    public boolean isAbierta() {
        return sala.isAbierta();
    }

    public String getUuid() {
        return uuid;
    }

    public List<Jugador> getJugadores() {
        return sala.getJugadores();
    }

    public String getHostId() {
        return sala.getHost() == null ? "" : sala.getHost().getId().toString();
    }

    public String getPantallaId() {
        return pantallaId;
    }

    public String getJuegoActual() {
        return juegoActual;
    }

    public String getP2pHostPeerId() {
        return p2pHostPeerId;
    }
}
