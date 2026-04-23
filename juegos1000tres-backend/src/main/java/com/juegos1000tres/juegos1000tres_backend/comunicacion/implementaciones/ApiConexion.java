package com.juegos1000tres.juegos1000tres_backend.comunicacion.implementaciones;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Conexion;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class ApiConexion implements Conexion<String> {

    private static final String TIPO_COMUNICACION = ComunicacionRuntimeConfig.apiTipoComunicacion();
    private static final String HOST_API = ComunicacionRuntimeConfig.apiHost();
    private static final String PLANTILLA_ENDPOINT_SALA = ComunicacionRuntimeConfig.apiEndpointSalaTemplate();
    private static final String PLANTILLA_ENDPOINT_ACTUALIZACIONES_SALA =
            ComunicacionRuntimeConfig.apiEndpointActualizacionesSalaTemplate();
    private static final int PUERTO_DEFECTO = ComunicacionRuntimeConfig.apiPuerto();
    private static final String PAYLOAD_INICIAL = ComunicacionRuntimeConfig.apiPayloadInicial();

    private final String salaId;
    private final String endpointSala;
    private final String endpointActualizacionesSala;
    private final int puerto;
    private final Queue<String> colaEntrante;

    private volatile boolean conectada;
    private volatile String ultimoMensaje;

    public ApiConexion(String salaId) {
        this(salaId, PUERTO_DEFECTO);
    }

    public ApiConexion(String salaId, int puerto) {
        this.salaId = validarSalaId(salaId);
        this.endpointSala = construirEndpointSala(this.salaId);
        this.endpointActualizacionesSala = construirEndpointActualizacionesSala(this.salaId);
        this.puerto = validarPuerto(puerto);
        this.colaEntrante = new ConcurrentLinkedQueue<>();
        this.conectada = false;
        this.ultimoMensaje = PAYLOAD_INICIAL;
        activarConexionInicial();
    }

    @Override
    public void conectar() {
        if (this.conectada) {
            return;
        }

        RuntimeEndpoints.registrar(this.puerto, this.endpointSala, new HandlerApiEventos(this));
        RuntimeEndpoints.registrar(this.puerto, this.endpointActualizacionesSala, new HandlerApiActualizaciones(this));
        this.conectada = true;
    }

    @Override
    public void desconectar() {
        if (!this.conectada) {
            return;
        }

        RuntimeEndpoints.desregistrar(this.endpointSala);
        RuntimeEndpoints.desregistrar(this.endpointActualizacionesSala);
        this.conectada = false;
    }

    @Override
    public void enviar(String payload) {
        validarConexionActiva();
        String payloadNoNulo = Objects.requireNonNull(payload, "El payload no puede ser nulo");
        this.ultimoMensaje = payloadNoNulo;
    }

    @Override
    public String recibir() {
        validarConexionActiva();
        String payload = this.colaEntrante.poll();
        if (payload == null) {
            return PAYLOAD_INICIAL;
        }

        return payload;
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
        return endpointSala;
    }

    public boolean isConectada() {
        return conectada;
    }

    public String getUrlEndpointSala() {
        return "http://" + HOST_API + ":" + this.puerto + this.endpointSala;
    }

    public String getEndpointActualizacionesSala() {
        return this.endpointActualizacionesSala;
    }

    public String getUrlEndpointActualizacionesSala() {
        return "http://" + HOST_API + ":" + this.puerto + this.endpointActualizacionesSala;
    }

    public static String construirEndpointSala(String salaId) {
        String salaIdValida = validarSalaId(salaId);
        return String.format(PLANTILLA_ENDPOINT_SALA, salaIdValida);
    }

    public static String construirEndpointActualizacionesSala(String salaId) {
        String salaIdValida = validarSalaId(salaId);
        return String.format(PLANTILLA_ENDPOINT_ACTUALIZACIONES_SALA, salaIdValida);
    }

    private void activarConexionInicial() {
        RuntimeEndpoints.registrar(this.puerto, this.endpointSala, new HandlerApiEventos(this));
        RuntimeEndpoints.registrar(this.puerto, this.endpointActualizacionesSala, new HandlerApiActualizaciones(this));
        this.conectada = true;
    }

    private void registrarMensajeEntrante(String payload) {
        String payloadNoNulo = Objects.requireNonNull(payload, "El payload no puede ser nulo");
        this.colaEntrante.offer(payloadNoNulo);
    }

    private void validarConexionActiva() {
        if (!this.conectada) {
            throw new IllegalStateException("La conexion API debe estar activa para enviar o recibir");
        }
    }

    private static int validarPuerto(int puerto) {
        if (puerto < 1 || puerto > 65535) {
            throw new IllegalArgumentException("El puerto de ApiConexion debe estar entre 1 y 65535");
        }

        return puerto;
    }

    private static String validarSalaId(String salaId) {
        if (salaId == null || salaId.isBlank()) {
            throw new IllegalArgumentException("El id de sala es obligatorio para ApiConexion");
        }

        return salaId.trim();
    }

    private static final class HandlerApiEventos implements HttpHandler {

        private final ApiConexion conexion;

        private HandlerApiEventos(ApiConexion conexion) {
            this.conexion = conexion;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();

            if (!this.conexion.isConectada()) {
                responderJson(exchange, 503,
                        "{\"status\":\"error\",\"message\":\"ApiConexion no activa\"}");
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                String payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                this.conexion.registrarMensajeEntrante(payload);
                responderJson(exchange, 202,
                        "{\"status\":\"accepted\",\"salaId\":\""
                                + escaparJson(this.conexion.getSalaId())
                                + "\",\"endpoint\":\""
                                + escaparJson(this.conexion.getUrlEndpointSala())
                                + "\"}");
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                responderJson(exchange, 200, this.conexion.ultimoMensaje);
                return;
            }

            responderJson(exchange, 405,
                    "{\"status\":\"error\",\"message\":\"Metodo no soportado\"}");
        }

        private void responderJson(HttpExchange exchange, int statusCode, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        }

        private String escaparJson(String value) {
            if (value == null) {
                return "";
            }

            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    private static final class HandlerApiActualizaciones implements HttpHandler {

        private final ApiConexion conexion;

        private HandlerApiActualizaciones(ApiConexion conexion) {
            this.conexion = conexion;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();

            if (!this.conexion.isConectada()) {
                responderJson(exchange, 503,
                        "{\"status\":\"error\",\"message\":\"ApiConexion no activa\"}");
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                String ultimoMensaje = this.conexion.ultimoMensaje;
                if (ultimoMensaje == null || ultimoMensaje.isBlank() || PAYLOAD_INICIAL.equals(ultimoMensaje.trim())) {
                    responderJson(exchange, 204, "");
                    return;
                }

                responderJson(exchange, 200, ultimoMensaje);
                return;
            }

            responderJson(exchange, 405,
                    "{\"status\":\"error\",\"message\":\"Metodo no soportado\"}");
        }

        private void responderJson(HttpExchange exchange, int statusCode, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        }
    }

    private static final class RuntimeEndpoints {
        private static final Object LOCK = new Object();
        private static final Set<String> ENDPOINTS_ACTIVOS = ConcurrentHashMap.newKeySet();

        private static HttpServer server;
        private static int puertoServer = -1;

        private RuntimeEndpoints() {
        }

        private static void registrar(int puerto, String endpoint, HttpHandler handler) {
            synchronized (LOCK) {
                iniciarServidorSiNecesario(puerto);

                if (ENDPOINTS_ACTIVOS.contains(endpoint)) {
                    throw new IllegalStateException("Ya existe un endpoint API activo para la sala: " + endpoint);
                }

                server.createContext(endpoint, handler);
                ENDPOINTS_ACTIVOS.add(endpoint);
            }
        }

        private static void desregistrar(String endpoint) {
            synchronized (LOCK) {
                if (server == null) {
                    return;
                }

                if (ENDPOINTS_ACTIVOS.remove(endpoint)) {
                    server.removeContext(endpoint);
                }

                if (ENDPOINTS_ACTIVOS.isEmpty()) {
                    server.stop(0);
                    server = null;
                    puertoServer = -1;
                }
            }
        }

        private static void iniciarServidorSiNecesario(int puerto) {
            if (server != null) {
                if (puertoServer != puerto) {
                    throw new IllegalStateException(
                            "ApiConexion ya tiene un servidor activo en puerto " + puertoServer
                                    + ". Debe usarse el mismo puerto para todos los endpoints temporales.");
                }

                return;
            }

            try {
                server = HttpServer.create(new InetSocketAddress(HOST_API, puerto), 0);
            } catch (IOException error) {
                throw new IllegalStateException("No se pudo iniciar el servidor temporal de ApiConexion", error);
            }

            server.setExecutor(null);
            server.start();
            puertoServer = puerto;
        }
    }
}