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
    private final String creadorUsuarioId;
    private String pantallaId;
    private String juegoActual;
    private String p2pHostPeerId;
    private boolean resultadosPersistidos;
    private int contadorNombres = 1;
    private int contadorInvitados = 1;
    private final Map<String, Integer> invitadosPorUsuario = new ConcurrentHashMap<>();

    public SalaRoom(String uuid, Sala sala, String creadorId, String creadorUsuarioId) {
        this.uuid = Objects.requireNonNull(uuid, "uuid requerido");
        this.sala = Objects.requireNonNull(sala, "sala requerida");
        this.creadorId = Objects.requireNonNull(creadorId, "creador requerido");
        this.creadorUsuarioId = creadorUsuarioId == null ? "" : creadorUsuarioId.trim();
        this.pantallaId = creadorId;
        this.juegoActual = "";
        this.p2pHostPeerId = "";
        this.resultadosPersistidos = false;
    }

    public synchronized Jugador agregarJugador(String nombre, String usuarioId, boolean esInvitado) {
        String nombreFinal = esInvitado
                ? resolverNombreInvitado(usuarioId)
                : resolverNombreJugador(nombre);
        Jugador jugador = new Jugador(nombreFinal, esInvitado ? null : usuarioId);
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
        this.resultadosPersistidos = false;
    }

    public synchronized void finalizarJuego(String actorId) {
        if (!creadorId.equals(actorId)) {
            throw new SecurityException("Solo el creador puede finalizar el juego");
        }

        this.juegoActual = "";
    }

    public synchronized boolean resultadosPersistidos() {
        return resultadosPersistidos;
    }

    public synchronized void marcarResultadosPersistidos() {
        this.resultadosPersistidos = true;
    }

    public synchronized void sumarVictoria(String jugadorId) {
        String canonicalId = resolverJugadorIdCanonical(jugadorId, null);
        UUID id = UUID.fromString(canonicalId);
        Jugador jugador = sala.getJugadores().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado"));

        jugador.sumarVictoria();
    }

    public synchronized void sumarPuntos(String jugadorId, int puntos) {
        String canonicalId = resolverJugadorIdCanonical(jugadorId, null);
        UUID id = UUID.fromString(canonicalId);
        Jugador jugador = sala.getJugadores().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado"));

        int antes = jugador.getPuntuacion();
        jugador.sumarPuntos(puntos);
        int despues = jugador.getPuntuacion();
        try {
            org.slf4j.LoggerFactory.getLogger(SalaRoom.class)
                    .info("sumarPuntos: sala={}, jugadorId={}, antes={}, despues={}", uuid, jugador.getId(), antes, despues);
        } catch (Throwable t) {
            // ignore logging problems
        }
    }

    public synchronized void establecerPuntuacion(String jugadorId, int puntuacion) {
        String canonicalId = resolverJugadorIdCanonical(jugadorId, null);
        UUID id = UUID.fromString(canonicalId);
        Jugador jugador = sala.getJugadores().stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado"));

        jugador.establecerPuntuacion(puntuacion);
    }

    public synchronized void reiniciarPuntuaciones() {
        for (Jugador jugador : sala.getJugadores()) {
            jugador.reiniciarPuntuacion();
        }
    }

    public synchronized String resolverJugadorIdCanonical(String jugadorId, String nombreJugador) {
        if (jugadorId != null && !jugadorId.isBlank()) {
            if (Objects.equals(this.p2pHostPeerId, jugadorId.trim())) {
                return this.creadorId;
            }

            UUID uuid = intentarParsearUuid(jugadorId);
            if (uuid != null) {
                boolean existe = sala.getJugadores().stream().anyMatch(item -> item.getId().equals(uuid));
                if (existe) {
                    return uuid.toString();
                }
            }

            String porUsuarioId = sala.getJugadores().stream()
                    .filter(item -> item.getUsuarioId() != null && item.getUsuarioId().equals(jugadorId.trim()))
                    .map(item -> item.getId().toString())
                    .findFirst()
                    .orElse(null);

            if (porUsuarioId != null) {
                return porUsuarioId;
            }
        }

        if (nombreJugador != null && !nombreJugador.isBlank()) {
            String nombre = nombreJugador.trim();
            List<Jugador> porNombre = sala.getJugadores().stream()
                    .filter(item -> item.getNombre().equalsIgnoreCase(nombre))
                    .toList();

            if (porNombre.size() == 1) {
                return porNombre.get(0).getId().toString();
            }
        }

        if (jugadorId == null || jugadorId.isBlank()) {
            throw new IllegalArgumentException("Jugador no encontrado");
        }

        return jugadorId.trim();
    }

    private UUID intentarParsearUuid(String valor) {
        try {
            return UUID.fromString(valor.trim());
        } catch (RuntimeException ex) {
            return null;
        }
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

    public String getCreadorUsuarioId() {
        return creadorUsuarioId;
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
