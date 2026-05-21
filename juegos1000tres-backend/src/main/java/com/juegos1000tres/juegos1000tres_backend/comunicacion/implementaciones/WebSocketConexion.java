package com.juegos1000tres.juegos1000tres_backend.comunicacion.implementaciones;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Conexion;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.DestinoEnvio;

public class WebSocketConexion implements Conexion<String> {

    private static final String TIPO_COMUNICACION = ComunicacionRuntimeConfig.websocketTipoComunicacion();
    private static final String HOST_WEBSOCKET = ComunicacionRuntimeConfig.websocketHost();
    private static final String PLANTILLA_CANAL_SALA = ComunicacionRuntimeConfig.websocketCanalSalaTemplate();
    private static final int PUERTO_DEFECTO = ComunicacionRuntimeConfig.websocketPuerto();
    private static final String PAYLOAD_VACIO = ComunicacionRuntimeConfig.websocketPayloadVacio();

    private final String salaId;
    private final String juego; // optional game name segment
    private final String canalSala;
    private final int puerto;
    private volatile boolean conectada;

    public WebSocketConexion(String salaId) {
        this(salaId, null, PUERTO_DEFECTO);
    }

    public WebSocketConexion(String salaId, int puerto) {
        this(salaId, null, puerto);
    }

    public WebSocketConexion(String salaId, String juego, int puerto) {
        this.salaId = validarSalaId(salaId);
        this.juego = (juego == null || juego.isBlank()) ? null : juego.trim();
        this.canalSala = construirCanalSala(this.salaId, this.juego);
        this.puerto = validarPuerto(puerto);
        this.conectada = false;
        activarConexionInicial();
    }

    @Override
    public void conectar() {
        if (this.conectada) {
            return;
        }

        RuntimeCanalesWebSocket.registrar(this.puerto, this.canalSala);
        this.conectada = true;
    }

    @Override
    public void desconectar() {
        if (!this.conectada) {
            return;
        }

        RuntimeCanalesWebSocket.desregistrar(this.canalSala);
        this.conectada = false;
    }

    @Override
    public void enviar(String payload) {
        validarConexionActiva();
        RuntimeCanalesWebSocket.enviar(this.canalSala, Objects.requireNonNull(payload, "El payload no puede ser nulo"));
    }

    @Override
    public void enviar(String payload, DestinoEnvio destinoEnvio) {
        validarConexionActiva();

        if (destinoEnvio == null || destinoEnvio.esGlobal() || destinoEnvio.esPantalla() || destinoEnvio.esVariosJugadores()) {
            enviar(payload);
            return;
        }

        if (destinoEnvio.esJugador()) {
            RuntimeCanalesWebSocket.enviarParaJugador(this.canalSala, destinoEnvio.getJugadorId(), Objects.requireNonNull(payload, "El payload no puede ser nulo"));
            return;
        }

        enviar(payload);
    }

    public void enviarParaJugador(String jugadorId, String payload) {
        validarConexionActiva();
        RuntimeCanalesWebSocket.enviarParaJugador(this.canalSala, jugadorId, Objects.requireNonNull(payload, "El payload no puede ser nulo"));
    }

    @Override
    public String recibir() {
        validarConexionActiva();
        return RuntimeCanalesWebSocket.recibir(this.canalSala);
    }

    @Override
    public Class<String> getClasePayload() {
        return String.class;
    }

    @Override
    public String getTipoComunicacion() {
        return TIPO_COMUNICACION;
    }

    @Override
    public String getSalaId() {
        return salaId;
    }

    @Override
    public String getCanalSala() {
        return canalSala;
    }

    public boolean isConectada() {
        return conectada;
    }

    public String getUrlCanalSala() {
        return "ws://" + HOST_WEBSOCKET + ":" + this.puerto + this.canalSala;
    }

    private void validarConexionActiva() {
        if (!conectada) {
            throw new IllegalStateException("La conexion WebSocket debe estar activa para enviar o recibir");
        }
    }

    private void activarConexionInicial() {
        RuntimeCanalesWebSocket.registrar(this.puerto, this.canalSala);
        this.conectada = true;
    }

    private static String construirCanalSala(String salaId) {
        return construirCanalSala(salaId, null);
    }

    private static String construirCanalSala(String salaId, String juego) {
        String salaValida = validarSalaId(salaId);
        if (juego == null || juego.isBlank()) {
            return String.format(PLANTILLA_CANAL_SALA, salaValida);
        }

        String plantilla = PLANTILLA_CANAL_SALA;
        if (plantilla.endsWith("/%s")) {
            // if template like /ws/salas/%s -> produce /ws/salas/{sala}/{juego}
            String prefix = plantilla.substring(0, plantilla.length() - "%s".length());
            return String.format(prefix + "%s", salaValida + "/" + juego.trim());
        }

        // Default fallback: append juego segment
        String base = String.format(plantilla, salaValida);
        return base + "/" + juego.trim();
    }

    private static String validarSalaId(String salaId) {
        if (salaId == null || salaId.isBlank()) {
            throw new IllegalArgumentException("El id de sala es obligatorio para WebSocketConexion");
        }

        return salaId.trim();
    }

    private static int validarPuerto(int puerto) {
        if (puerto < 1 || puerto > 65535) {
            throw new IllegalArgumentException("El puerto de WebSocketConexion debe estar entre 1 y 65535");
        }

        return puerto;
    }

    private static final class RuntimeCanalesWebSocket {
        private static final Object LOCK = new Object();
        private static final ConcurrentHashMap<String, EstadoCanal> ESTADO_POR_CANAL = new ConcurrentHashMap<>();

        private static ServidorWebSocketTemporal server;
        private static int puertoServer = -1;

        private RuntimeCanalesWebSocket() {
        }

        private static void registrar(int puerto, String canal) {
            synchronized (LOCK) {
                iniciarServidorSiNecesario(puerto);
                ESTADO_POR_CANAL.compute(canal, (_canal, estadoExistente) -> {
                    if (estadoExistente == null) {
                        return new EstadoCanal();
                    }

                    estadoExistente.activo = true;
                    return estadoExistente;
                });
            }
        }

        private static void desregistrar(String canal) {
            synchronized (LOCK) {
                EstadoCanal estado = ESTADO_POR_CANAL.remove(canal);
                if (estado != null) {
                    for (WebSocket cliente : estado.clientes) {
                        if (cliente != null && cliente.isOpen()) {
                            cliente.close(1000, "Canal cerrado");
                        }
                    }
                }

                if (ESTADO_POR_CANAL.isEmpty() && server != null) {
                    try {
                        server.stop();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupcion al detener el servidor WebSocket temporal", ex);
                    }
                    server = null;
                    puertoServer = -1;
                }
            }
        }

        private static void enviar(String canal, String payload) {
            EstadoCanal estado = obtenerCanalActivo(canal);

            for (WebSocket cliente : estado.clientes) {
                if (cliente != null && cliente.isOpen()) {
                    cliente.send(payload);
                }
            }
        }

        private static void enviarParaJugador(String canal, String jugadorId, String payload) {
            if (jugadorId == null || jugadorId.isBlank()) {
                enviar(canal, payload);
                return;
            }

            EstadoCanal estado = obtenerCanalActivo(canal);

            for (WebSocket cliente : estado.clientes) {
                if (cliente == null || !cliente.isOpen()) continue;
                String descriptor = cliente.getResourceDescriptor();
                if (descriptor == null) continue;
                int idx = descriptor.indexOf('?');
                String query = idx >= 0 ? descriptor.substring(idx + 1) : "";
                // parse simple query param jugadorId
                String[] parts = query.split("&");
                for (String p : parts) {
                    String[] kv = p.split("=", 2);
                    if (kv.length == 2 && kv[0].equalsIgnoreCase("jugadorId") && kv[1].equals(jugadorId)) {
                        cliente.send(payload);
                        break;
                    }
                }
            }
        }

        private static String recibir(String canal) {
            EstadoCanal estado = obtenerCanalActivo(canal);
            String mensaje = estado.bufferMensajes.poll();
            return mensaje == null ? PAYLOAD_VACIO : mensaje;
        }

        private static void registrarCliente(String canal, WebSocket cliente) {
            EstadoCanal estado = obtenerCanalActivo(canal);
            estado.clientes.add(cliente);
        }

        private static void eliminarCliente(WebSocket cliente) {
            if (cliente == null) {
                return;
            }

            for (EstadoCanal estado : ESTADO_POR_CANAL.values()) {
                estado.clientes.remove(cliente);
            }
        }

        private static void registrarMensajeEntrante(WebSocket cliente, String mensaje) {
            if (cliente == null) {
                return;
            }

            String canal = extraerCanal(cliente);
            EstadoCanal estado = ESTADO_POR_CANAL.get(canal);
            if (estado != null && estado.activo) {
                estado.bufferMensajes.offer(mensaje == null ? PAYLOAD_VACIO : mensaje);
            }
        }

        private static String extraerCanal(WebSocket cliente) {
            String descriptor = cliente.getResourceDescriptor();
            if (descriptor == null || descriptor.isBlank()) {
                return "";
            }

            int idxQuery = descriptor.indexOf('?');
            return idxQuery >= 0 ? descriptor.substring(0, idxQuery) : descriptor;
        }

        private static EstadoCanal obtenerCanalActivo(String canal) {
            EstadoCanal estado = ESTADO_POR_CANAL.get(canal);
            if (estado == null || !estado.activo) {
                throw new IllegalStateException("El canal WebSocket no esta activo: " + canal);
            }

            return estado;
        }

        private static void iniciarServidorSiNecesario(int puerto) {
            if (server != null) {
                if (puertoServer != puerto) {
                    throw new IllegalStateException(
                            "WebSocketConexion ya tiene un servidor activo en puerto " + puertoServer
                                    + ". Debe usarse el mismo puerto para todos los canales temporales.");
                }

                return;
            }

            server = new ServidorWebSocketTemporal(new InetSocketAddress(HOST_WEBSOCKET, puerto));
            server.start();
            puertoServer = puerto;
        }
    }

    private static final class EstadoCanal {
        private volatile boolean activo;
        private final Queue<String> bufferMensajes;
        private final Set<WebSocket> clientes;

        private EstadoCanal() {
            this.activo = true;
            this.bufferMensajes = new ConcurrentLinkedQueue<>();
            this.clientes = ConcurrentHashMap.newKeySet();
        }
    }

    private static final class ServidorWebSocketTemporal extends WebSocketServer {

        private ServidorWebSocketTemporal(InetSocketAddress address) {
            super(address);
            setReuseAddr(true);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String canal = handshake == null ? "" : handshake.getResourceDescriptor();
            int idxQuery = canal.indexOf('?');
            if (idxQuery >= 0) {
                canal = canal.substring(0, idxQuery);
            }

            try {
                RuntimeCanalesWebSocket.registrarCliente(canal, conn);
            } catch (IllegalStateException ex) {
                conn.close(1008, ex.getMessage());
            }
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            RuntimeCanalesWebSocket.eliminarCliente(conn);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            RuntimeCanalesWebSocket.registrarMensajeEntrante(conn, message);
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            // No-op: el juego puede decidir si inspecciona errores externamente.
        }

        @Override
        public void onStart() {
            // No-op: servidor listo para aceptar conexiones.
        }
    }
}