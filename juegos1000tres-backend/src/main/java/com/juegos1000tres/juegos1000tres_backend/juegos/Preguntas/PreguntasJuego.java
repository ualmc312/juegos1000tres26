package com.juegos1000tres.juegos1000tres_backend.juegos.Preguntas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.ContextoEvento;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Recibo;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.modelos.Juego;

public class PreguntasJuego extends Juego {

    public static final String COMANDO_REGISTRAR_JUGADOR = "REGISTRAR_JUGADOR";
    public static final String COMANDO_INICIAR_RONDA = "INICIAR_RONDA";
    public static final String COMANDO_ACTUALIZAR_BORRADOR = "ACTUALIZAR_BORRADOR";
    public static final String COMANDO_ENVIAR_RESPUESTA = "ENVIAR_RESPUESTA";
    public static final String COMANDO_ELEGIR_RESPUESTA = "ELEGIR_RESPUESTA";
    public static final String COMANDO_ESTADO_PARTIDA = "ESTADO_PREGUNTAS";

    private static final int LIMITE_RESPUESTA = 120;
    private static final int TIEMPO_RESPUESTA_SEGUNDOS = 30;
    private static final long DEMORA_SIGUIENTE_RONDA_MS = 5000L;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, JugadorInterno> jugadores;
    private final List<String> bancoPreguntas;
    private final List<OpcionInterna> opcionesActuales;
    private final Map<String, String> borradoresPorJugador;
    private final Set<String> respuestasConfirmadas;
    private final Set<String> respondedoresEsperados;
    private final Random random;

    private FaseRonda faseRonda;
    private boolean enCurso;
    private int rondaActual;
    private long deadlineRespuestasEpochMs;
    private long proximaRondaEpochMs;
    private String jugadorElegidoId;
    private String preguntaActual;
    private String opcionGanadoraId;
    private String mensajeEstado;

    public PreguntasJuego(
            int numeroJugadores,
            Traductor<?> conexionJugadores,
            Traductor<?> conexionPantalla,
            Collection<String> preguntasDisponibles) {
        super(numeroJugadores, true, conexionJugadores, conexionPantalla);
        this.jugadores = new LinkedHashMap<>();
        this.bancoPreguntas = normalizarPreguntas(preguntasDisponibles);
        this.opcionesActuales = new ArrayList<>();
        this.borradoresPorJugador = new LinkedHashMap<>();
        this.respuestasConfirmadas = new LinkedHashSet<>();
        this.respondedoresEsperados = new LinkedHashSet<>();
        this.random = new Random();
        this.faseRonda = FaseRonda.ESPERANDO_JUGADORES;
        this.enCurso = false;
        this.rondaActual = 0;
        this.deadlineRespuestasEpochMs = 0L;
        this.proximaRondaEpochMs = 0L;
        this.jugadorElegidoId = null;
        this.preguntaActual = "";
        this.opcionGanadoraId = null;
        this.mensajeEstado = "Esperando al menos 2 jugadores para iniciar";
    }

    public Recibo<String> registrarEventosEnRecibo(Recibo<String> reciboBase) {
        Objects.requireNonNull(reciboBase, "El recibo base es obligatorio");

        Recibo<String> reciboConRegistro = reciboBase.conEvento(
                COMANDO_REGISTRAR_JUGADOR,
                new RegistrarJugadorPreguntasEvento(this));

        Recibo<String> reciboConInicio = reciboConRegistro.conEvento(
                COMANDO_INICIAR_RONDA,
                new IniciarRondaPreguntasEvento(this));

        Recibo<String> reciboConBorrador = reciboConInicio.conEvento(
                COMANDO_ACTUALIZAR_BORRADOR,
                new ActualizarBorradorRespuestaPreguntasEvento(this));

        Recibo<String> reciboConRespuesta = reciboConBorrador.conEvento(
                COMANDO_ENVIAR_RESPUESTA,
                new EnviarRespuestaPreguntasEvento(this));

        return reciboConRespuesta.conEvento(
                COMANDO_ELEGIR_RESPUESTA,
                new ElegirRespuestaPreguntasEvento(this));
    }

    public synchronized void registrarJugadorDesdePayload(String payload, ContextoEvento contexto) {
        Objects.requireNonNull(contexto, "El contexto de evento es obligatorio");
        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_REGISTRAR_JUGADOR);

        String jugadorId = leerTextoObligatorio(data, "jugadorId");
        String nombreJugador = leerTextoObligatorio(data, "nombreJugador");

        JugadorInterno jugadorExistente = this.jugadores.get(jugadorId);
        if (jugadorExistente == null) {
            this.jugadores.put(jugadorId, new JugadorInterno(jugadorId, nombreJugador));
        } else {
            jugadorExistente.setNombre(nombreJugador);
        }

        long ahoraMs = System.currentTimeMillis();
        if (this.faseRonda == FaseRonda.ESPERANDO_JUGADORES && this.jugadores.size() >= 2) {
            iniciarNuevaRondaInterna(ahoraMs);
        } else if (this.jugadores.size() < 2) {
            this.mensajeEstado = "Esperando al menos 2 jugadores para iniciar";
        }

        contexto.enviar(crearEstadoEnviable(ahoraMs));
    }

    public synchronized void iniciarRondaDesdePayload(String payload, ContextoEvento contexto) {
        Objects.requireNonNull(contexto, "El contexto de evento es obligatorio");
        leerPayloadComoMapa(payload, COMANDO_INICIAR_RONDA);

        long ahoraMs = System.currentTimeMillis();
        if (this.jugadores.size() < 2) {
            this.faseRonda = FaseRonda.ESPERANDO_JUGADORES;
            this.enCurso = false;
            this.mensajeEstado = "Se necesitan al menos 2 jugadores";
            contexto.enviar(crearEstadoEnviable(ahoraMs));
            return;
        }

        if (this.faseRonda == FaseRonda.RESPONDIENDO || this.faseRonda == FaseRonda.ELEGIENDO) {
            contexto.enviar(crearEstadoEnviable(ahoraMs));
            return;
        }

        iniciarNuevaRondaInterna(ahoraMs);
        contexto.enviar(crearEstadoEnviable(ahoraMs));
    }

    public synchronized void actualizarBorradorDesdePayload(String payload, ContextoEvento contexto) {
        Objects.requireNonNull(contexto, "El contexto de evento es obligatorio");

        if (this.faseRonda != FaseRonda.RESPONDIENDO) {
            return;
        }

        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_ACTUALIZAR_BORRADOR);
        String jugadorId = leerTextoObligatorio(data, "jugadorId");
        String respuesta = sanitizarRespuesta(leerTextoOpcional(data, "respuesta"));

        if (!this.respondedoresEsperados.contains(jugadorId)) {
            return;
        }

        this.borradoresPorJugador.put(jugadorId, respuesta);
    }

    public synchronized void enviarRespuestaDesdePayload(String payload, ContextoEvento contexto) {
        Objects.requireNonNull(contexto, "El contexto de evento es obligatorio");
        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_ENVIAR_RESPUESTA);

        String jugadorId = leerTextoObligatorio(data, "jugadorId");
        String respuesta = sanitizarRespuesta(leerTextoOpcional(data, "respuesta"));

        if (this.faseRonda != FaseRonda.RESPONDIENDO) {
            contexto.enviar(crearEstadoEnviable(System.currentTimeMillis()));
            return;
        }

        if (Objects.equals(jugadorId, this.jugadorElegidoId)) {
            throw new IllegalArgumentException("El jugador elegido no debe responder");
        }

        if (!this.respondedoresEsperados.contains(jugadorId)) {
            contexto.enviar(crearEstadoEnviable(System.currentTimeMillis()));
            return;
        }

        this.borradoresPorJugador.put(jugadorId, respuesta);
        this.respuestasConfirmadas.add(jugadorId);

        long ahoraMs = System.currentTimeMillis();
        if (todasLasRespuestasConfirmadas() || ahoraMs >= this.deadlineRespuestasEpochMs) {
            cerrarFaseRespuestasInterna(ahoraMs);
        }

        contexto.enviar(crearEstadoEnviable(ahoraMs));
    }

    public synchronized void elegirRespuestaDesdePayload(String payload, ContextoEvento contexto) {
        Objects.requireNonNull(contexto, "El contexto de evento es obligatorio");
        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_ELEGIR_RESPUESTA);

        String jugadorId = leerTextoObligatorio(data, "jugadorId");
        String opcionId = leerTextoObligatorio(data, "opcionId");

        if (this.faseRonda != FaseRonda.ELEGIENDO) {
            throw new IllegalStateException("No hay una ronda en fase de eleccion");
        }

        if (!Objects.equals(jugadorId, this.jugadorElegidoId)) {
            throw new IllegalArgumentException("Solo el jugador elegido puede seleccionar una opcion");
        }

        OpcionInterna opcionSeleccionada = null;
        for (OpcionInterna opcion : this.opcionesActuales) {
            if (opcion.opcionId.equals(opcionId)) {
                opcionSeleccionada = opcion;
                break;
            }
        }

        if (opcionSeleccionada == null) {
            throw new IllegalArgumentException("La opcion seleccionada no existe");
        }

        if (!opcionSeleccionada.seleccionable) {
            throw new IllegalArgumentException("No se puede seleccionar una respuesta vacia");
        }

        this.opcionGanadoraId = opcionSeleccionada.opcionId;
        JugadorInterno ganador = this.jugadores.get(opcionSeleccionada.autorJugadorId);
        if (ganador != null) {
            ganador.puntos += 1;
        }

        this.faseRonda = FaseRonda.MOSTRANDO_RESULTADO;
        this.proximaRondaEpochMs = System.currentTimeMillis() + DEMORA_SIGUIENTE_RONDA_MS;
        this.mensajeEstado = ganador == null
                ? "Respuesta seleccionada"
                : "Punto para " + ganador.nombre;

        contexto.enviar(crearEstadoEnviable(System.currentTimeMillis()));
    }

    public synchronized boolean revisarTransicionesAutomaticas(long ahoraMs) {
        boolean huboCambios = false;

        if (this.faseRonda == FaseRonda.RESPONDIENDO
                && this.deadlineRespuestasEpochMs > 0
                && ahoraMs >= this.deadlineRespuestasEpochMs) {
            cerrarFaseRespuestasInterna(ahoraMs);
            huboCambios = true;
        }

        if (this.faseRonda == FaseRonda.MOSTRANDO_RESULTADO
                && this.proximaRondaEpochMs > 0
                && ahoraMs >= this.proximaRondaEpochMs) {
            if (this.jugadores.size() >= 2) {
                iniciarNuevaRondaInterna(ahoraMs);
            } else {
                this.faseRonda = FaseRonda.ESPERANDO_JUGADORES;
                this.enCurso = false;
                this.mensajeEstado = "Esperando al menos 2 jugadores para iniciar";
            }
            huboCambios = true;
        }

        return huboCambios;
    }

    public synchronized PreguntasEstadoEnviable crearEstadoEnviable() {
        return crearEstadoEnviable(System.currentTimeMillis());
    }

    public synchronized PreguntasEstadoEnviable crearEstadoEnviable(long ahoraMs) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("comando", COMANDO_ESTADO_PARTIDA);
        estado.put("fase", this.faseRonda.name());
        estado.put("rondaActual", this.rondaActual);
        estado.put("mensaje", this.mensajeEstado);
        estado.put("tiempoLimiteRespuestaSegundos", TIEMPO_RESPUESTA_SEGUNDOS);

        long tiempoRestanteMs = this.faseRonda == FaseRonda.RESPONDIENDO
                ? Math.max(0L, this.deadlineRespuestasEpochMs - ahoraMs)
                : 0L;
        estado.put("tiempoRestanteMs", tiempoRestanteMs);

        if (this.jugadorElegidoId != null) {
            JugadorInterno elegido = this.jugadores.get(this.jugadorElegidoId);
            if (elegido != null) {
                Map<String, Object> elegidoMap = new LinkedHashMap<>();
                elegidoMap.put("jugadorId", elegido.jugadorId);
                elegidoMap.put("nombreJugador", elegido.nombre);
                estado.put("jugadorElegido", elegidoMap);
            }
        }

        estado.put("preguntaActual", this.preguntaActual == null ? "" : this.preguntaActual);
        estado.put("respuestasEsperadas", this.respondedoresEsperados.size());
        estado.put("respuestasRecibidas", this.respuestasConfirmadas.size());
        estado.put("respondedoresPendientes", construirRespondedoresPendientes());
        estado.put("opciones", construirOpcionesParaEstado());
        estado.put("marcador", construirMarcadorOrdenado());
        estado.put("puedeIniciarRonda",
                this.jugadores.size() >= 2
                        && (this.faseRonda == FaseRonda.ESPERANDO_JUGADORES
                                || this.faseRonda == FaseRonda.MOSTRANDO_RESULTADO));

        return new PreguntasEstadoEnviable(estado);
    }

    public synchronized Set<String> getJugadoresRegistradosIds() {
        return Set.copyOf(this.jugadores.keySet());
    }

    public synchronized int getNumeroJugadoresRegistrados() {
        return this.jugadores.size();
    }

    @Override
    public synchronized void procesarMensajeEntrante(Enviable mensaje) {
        Objects.requireNonNull(mensaje, "El mensaje entrante es obligatorio");

        if (!(mensaje instanceof PreguntasEstadoEnviable estadoRecibido)) {
            throw new IllegalArgumentException("Tipo de mensaje no soportado para Preguntas");
        }

        Map<String, Object> estado = estadoRecibido.getEstado();
        Object fase = estado.get("fase");
        if (fase instanceof String faseTexto) {
            try {
                this.faseRonda = FaseRonda.valueOf(faseTexto);
            } catch (IllegalArgumentException _error) {
                this.faseRonda = FaseRonda.ESPERANDO_JUGADORES;
            }
        }
    }

    @Override
    public synchronized void iniciar() {
        this.enCurso = true;
    }

    @Override
    public synchronized void terminar() {
        this.enCurso = false;
        this.faseRonda = FaseRonda.ESPERANDO_JUGADORES;
        this.deadlineRespuestasEpochMs = 0L;
        this.proximaRondaEpochMs = 0L;
        this.opcionGanadoraId = null;
        this.opcionesActuales.clear();
        this.respondedoresEsperados.clear();
        this.respuestasConfirmadas.clear();
        this.borradoresPorJugador.clear();
        this.mensajeEstado = "Partida detenida";
    }

    private void iniciarNuevaRondaInterna(long ahoraMs) {
        if (this.jugadores.size() < 2) {
            this.faseRonda = FaseRonda.ESPERANDO_JUGADORES;
            this.enCurso = false;
            this.mensajeEstado = "Esperando al menos 2 jugadores para iniciar";
            return;
        }

        List<String> jugadoresActuales = new ArrayList<>(this.jugadores.keySet());
        this.jugadorElegidoId = jugadoresActuales.get(this.random.nextInt(jugadoresActuales.size()));

        this.respondedoresEsperados.clear();
        for (String jugadorId : jugadoresActuales) {
            if (!Objects.equals(jugadorId, this.jugadorElegidoId)) {
                this.respondedoresEsperados.add(jugadorId);
            }
        }

        this.rondaActual += 1;
        this.faseRonda = FaseRonda.RESPONDIENDO;
        this.enCurso = true;
        this.deadlineRespuestasEpochMs = ahoraMs + (TIEMPO_RESPUESTA_SEGUNDOS * 1000L);
        this.proximaRondaEpochMs = 0L;
        this.opcionGanadoraId = null;
        this.opcionesActuales.clear();
        this.respuestasConfirmadas.clear();
        this.borradoresPorJugador.clear();

        for (String jugadorId : this.respondedoresEsperados) {
            this.borradoresPorJugador.put(jugadorId, "");
        }

        JugadorInterno jugadorElegido = this.jugadores.get(this.jugadorElegidoId);
        this.preguntaActual = construirPreguntaParaJugador(jugadorElegido);
        this.mensajeEstado = "Ronda " + this.rondaActual + ": todos responden sobre " + jugadorElegido.nombre;
    }

    private void cerrarFaseRespuestasInterna(long ahoraMs) {
        this.deadlineRespuestasEpochMs = 0L;
        this.opcionGanadoraId = null;
        this.opcionesActuales.clear();

        for (String jugadorId : this.respondedoresEsperados) {
            String texto = sanitizarRespuesta(this.borradoresPorJugador.get(jugadorId));
            this.opcionesActuales.add(new OpcionInterna(UUID.randomUUID().toString(), jugadorId, texto, !texto.isBlank()));
        }

        Collections.shuffle(this.opcionesActuales, this.random);

        boolean hayOpcionesSeleccionables = this.opcionesActuales.stream().anyMatch((opcion) -> opcion.seleccionable);
        JugadorInterno jugadorElegido = this.jugadores.get(this.jugadorElegidoId);

        if (!hayOpcionesSeleccionables) {
            this.faseRonda = FaseRonda.MOSTRANDO_RESULTADO;
            this.proximaRondaEpochMs = ahoraMs + DEMORA_SIGUIENTE_RONDA_MS;
            this.mensajeEstado = "No hubo respuestas seleccionables esta ronda";
            return;
        }

        this.faseRonda = FaseRonda.ELEGIENDO;
        this.mensajeEstado = "Turno de " + jugadorElegido.nombre + " para elegir su respuesta favorita";
    }

    private List<Map<String, Object>> construirRespondedoresPendientes() {
        List<Map<String, Object>> pendientes = new ArrayList<>();

        for (String jugadorId : this.respondedoresEsperados) {
            if (this.respuestasConfirmadas.contains(jugadorId)) {
                continue;
            }

            JugadorInterno jugador = this.jugadores.get(jugadorId);
            if (jugador == null) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jugadorId", jugador.jugadorId);
            item.put("nombreJugador", jugador.nombre);
            pendientes.add(item);
        }

        pendientes.sort(Comparator.comparing((item) -> (String) item.get("nombreJugador")));
        return pendientes;
    }

    private List<Map<String, Object>> construirOpcionesParaEstado() {
        boolean mostrarAutores = this.faseRonda == FaseRonda.MOSTRANDO_RESULTADO;

        List<Map<String, Object>> opciones = new ArrayList<>();
        for (OpcionInterna opcion : this.opcionesActuales) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("opcionId", opcion.opcionId);
            item.put("texto", opcion.texto);
            item.put("seleccionable", opcion.seleccionable);
            item.put("seleccionada", Objects.equals(opcion.opcionId, this.opcionGanadoraId));

            if (mostrarAutores) {
                JugadorInterno autor = this.jugadores.get(opcion.autorJugadorId);
                item.put("autorJugadorId", opcion.autorJugadorId);
                item.put("autorNombre", autor == null ? "Desconocido" : autor.nombre);
            }

            opciones.add(item);
        }

        return opciones;
    }

    private List<Map<String, Object>> construirMarcadorOrdenado() {
        List<JugadorInterno> ordenados = new ArrayList<>(this.jugadores.values());
        ordenados.sort(
                Comparator.comparingInt((JugadorInterno jugador) -> jugador.puntos)
                        .reversed()
                        .thenComparing((JugadorInterno jugador) -> jugador.nombre));

        List<Map<String, Object>> marcador = new ArrayList<>();
        for (JugadorInterno jugador : ordenados) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jugadorId", jugador.jugadorId);
            item.put("nombreJugador", jugador.nombre);
            item.put("puntos", jugador.puntos);
            marcador.add(item);
        }

        return marcador;
    }

    private String construirPreguntaParaJugador(JugadorInterno jugadorElegido) {
        String plantilla = this.bancoPreguntas.get(this.random.nextInt(this.bancoPreguntas.size()));
        String nombre = jugadorElegido == null ? "este jugador" : jugadorElegido.nombre;

        return plantilla
                .replace("[NOMBRE_JUGADOR]", nombre)
                .replace("{NOMBRE_JUGADOR}", nombre)
                .replace("<NOMBRE_JUGADOR>", nombre);
    }

    private boolean todasLasRespuestasConfirmadas() {
        return this.respuestasConfirmadas.containsAll(this.respondedoresEsperados);
    }

    private Map<String, Object> leerPayloadComoMapa(String payload, String comandoEsperado) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("El payload de " + comandoEsperado + " no puede estar vacio");
        }

        try {
            Map<String, Object> data = OBJECT_MAPPER.readValue(payload, MAP_TYPE);
            String comando = leerTextoObligatorio(data, "comando");
            if (!comandoEsperado.equalsIgnoreCase(comando)) {
                throw new IllegalArgumentException("Comando inesperado para evento " + comandoEsperado);
            }
            return data;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("No se pudo interpretar el payload de " + comandoEsperado, error);
        }
    }

    private String leerTextoObligatorio(Map<String, Object> data, String campo) {
        Object value = data.get(campo);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Falta el campo '" + campo + "'");
        }

        return text.trim();
    }

    private String leerTextoOpcional(Map<String, Object> data, String campo) {
        Object value = data.get(campo);
        if (!(value instanceof String text)) {
            return "";
        }

        return text.trim();
    }

    private String sanitizarRespuesta(String respuesta) {
        if (respuesta == null) {
            return "";
        }

        String texto = respuesta.trim().replace("\n", " ").replace("\r", " ");
        if (texto.length() <= LIMITE_RESPUESTA) {
            return texto;
        }

        return texto.substring(0, LIMITE_RESPUESTA);
    }

    private List<String> normalizarPreguntas(Collection<String> preguntasDisponibles) {
        List<String> preguntas = new ArrayList<>();
        if (preguntasDisponibles != null) {
            for (String pregunta : preguntasDisponibles) {
                if (pregunta == null || pregunta.isBlank()) {
                    continue;
                }
                preguntas.add(pregunta.trim());
            }
        }

        if (preguntas.isEmpty()) {
            preguntas.add("Cual es la comida favorita de [NOMBRE_JUGADOR]?");
        }

        return preguntas;
    }

    private enum FaseRonda {
        ESPERANDO_JUGADORES,
        RESPONDIENDO,
        ELEGIENDO,
        MOSTRANDO_RESULTADO
    }

    private static final class JugadorInterno {
        private final String jugadorId;
        private String nombre;
        private int puntos;

        private JugadorInterno(String jugadorId, String nombre) {
            this.jugadorId = jugadorId;
            this.nombre = nombre;
            this.puntos = 0;
        }

        private void setNombre(String nombre) {
            this.nombre = nombre;
        }
    }

    private static final class OpcionInterna {
        private final String opcionId;
        private final String autorJugadorId;
        private final String texto;
        private final boolean seleccionable;

        private OpcionInterna(String opcionId, String autorJugadorId, String texto, boolean seleccionable) {
            this.opcionId = opcionId;
            this.autorJugadorId = autorJugadorId;
            this.texto = texto;
            this.seleccionable = seleccionable;
        }
    }
}
