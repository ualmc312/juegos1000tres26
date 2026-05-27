package com.juegos1000tres.juegos1000tres_backend.juegos.HablameDeTi;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Recibo;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.modelos.Juego;
import com.juegos1000tres.juegos1000tres_backend.sala.SalaService;

public class HablameDeTiJuego extends Juego {

    public static final String COMANDO_REGISTRAR_JUGADOR = "REGISTRAR_JUGADOR";
    public static final String COMANDO_INICIAR_PARTIDA = "INICIAR_PARTIDA";
    public static final String COMANDO_RESPONDER_PREGUNTA = "RESPONDER_PREGUNTA";
    public static final String COMANDO_ENVIAR_MENTIRA = "ENVIAR_MENTIRA";
    public static final String COMANDO_VOTAR_RESPUESTA = "VOTAR_RESPUESTA";
    public static final String COMANDO_ESTADO_PARTIDA = "ESTADO_HABLAME_DE_TI";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final long DEMORA_RESULTADO_MS = 6000L;

    private final Map<String, JugadorPartida> jugadores = new LinkedHashMap<>();
    private final List<PreguntaBanco> bancoPreguntas;
    private final Map<String, PreguntaAsignada> asignaciones = new LinkedHashMap<>();
    private final Map<String, String> respuestasOriginales = new LinkedHashMap<>();
    private final Map<String, String> mentirasPorJugador = new LinkedHashMap<>();
    private final Map<String, String> votosPorJugador = new LinkedHashMap<>();
    private final Set<String> mentirasNormalizadas = new LinkedHashSet<>();
    private final Set<String> ganadores = new LinkedHashSet<>();
    private final List<String> ordenRondas = new ArrayList<>();
    private final List<OpcionRespuesta> opcionesActuales = new ArrayList<>();
    private final List<Map<String, Object>> resumenRondaActual = new ArrayList<>();
    private final Random random = new Random();
    private final SalaService salaService;
    private final String salaId;

    private FasePartida fase = FasePartida.ESPERANDO_JUGADORES;
    private boolean enCurso;
    private int indiceRondaActual;
    private String jugadorObjetivoId;
    private String preguntaDirectaActual = "";
    private String preguntaPublicaActual = "";
    private String respuestaOriginalActual = "";
    private String mensajeEstado = "Esperando al menos 2 jugadores";
    private String ultimoError = "";
    private long proximaTransicionEpochMs;

    public HablameDeTiJuego(Traductor<?> conexionJugadores, Traductor<?> conexionPantalla, SalaService salaService, String salaId) {
        super(100, true, conexionJugadores, conexionPantalla);
        this.bancoPreguntas = cargarBancoPreguntas();
        this.salaService = Objects.requireNonNull(salaService, "SalaService es obligatorio");
        this.salaId = Objects.requireNonNull(salaId, "SalaId es obligatorio");
    }

    public Recibo<String> registrarEventosEnRecibo(Recibo<String> reciboBase) {
        Objects.requireNonNull(reciboBase, "El recibo base es obligatorio");

        return reciboBase
                .conEvento(COMANDO_REGISTRAR_JUGADOR, new RegistrarJugadorEvento(this))
                .conEvento(COMANDO_INICIAR_PARTIDA, new IniciarPartidaEvento(this))
                .conEvento(COMANDO_RESPONDER_PREGUNTA, new ResponderPreguntaEvento(this))
                .conEvento(COMANDO_ENVIAR_MENTIRA, new EnviarMentiraEvento(this))
                .conEvento(COMANDO_VOTAR_RESPUESTA, new VotarRespuestaEvento(this));
    }

    public synchronized void registrarJugadorDesdePayload(String payload) {
        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_REGISTRAR_JUGADOR);
        String jugadorId = leerTextoObligatorio(data, "jugadorId");
        String nombreJugador = leerTextoOpcional(data, "nombreJugador");

        if (this.fase != FasePartida.ESPERANDO_JUGADORES && this.fase != FasePartida.RESPONDIENDO_ORIGINALES) {
            registrarError("La partida ya ha comenzado");
            return;
        }

        JugadorPartida jugador = this.jugadores.computeIfAbsent(jugadorId,
                id -> new JugadorPartida(id, nombreJugador == null || nombreJugador.isBlank() ? "Jugador" : nombreJugador.trim()));
        if (nombreJugador != null && !nombreJugador.isBlank()) {
            jugador.nombreJugador = nombreJugador.trim();
        }

        if (this.jugadores.size() < 2) {
            this.mensajeEstado = "Esperando al menos 2 jugadores";
        }

        publicarEstado();
    }

    public synchronized void iniciarPartidaDesdePayload(String payload) {
        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_INICIAR_PARTIDA);
        leerTextoOpcional(data, "actorId");

        if (this.jugadores.size() < 2) {
            registrarError("Se necesitan al menos 2 jugadores");
            return;
        }

        iniciarPartidaInterna();
        publicarEstado();
    }

    public synchronized void responderPreguntaDesdePayload(String payload) {
        if (this.fase != FasePartida.RESPONDIENDO_ORIGINALES) {
            registrarError("Todavia no toca responder preguntas");
            return;
        }

        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_RESPONDER_PREGUNTA);
        String jugadorId = leerTextoObligatorio(data, "jugadorId");
        String respuesta = normalizarTexto(leerTextoObligatorio(data, "respuesta"));

        if (!this.asignaciones.containsKey(jugadorId)) {
            registrarError("El jugador no tiene pregunta asignada");
            return;
        }

        if (this.respuestasOriginales.containsKey(jugadorId)) {
            registrarError("La respuesta ya fue enviada");
            return;
        }

        this.respuestasOriginales.put(jugadorId, respuesta);
        if (this.respuestasOriginales.size() == this.jugadores.size()) {
            prepararRondaActual();
        }

        publicarEstado();
    }

    public synchronized void enviarMentiraDesdePayload(String payload) {
        if (this.fase != FasePartida.ESCRIBIENDO_MENTIRAS) {
            registrarError("Todavia no toca escribir mentiras");
            return;
        }

        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_ENVIAR_MENTIRA);
        String jugadorId = leerTextoObligatorio(data, "jugadorId");
        String respuesta = normalizarTexto(leerTextoObligatorio(data, "respuesta"));

        if (Objects.equals(jugadorId, this.jugadorObjetivoId)) {
            registrarError("El jugador original no escribe mentira");
            return;
        }

        if (this.mentirasPorJugador.containsKey(jugadorId)) {
            registrarError("Ya has enviado tu respuesta");
            return;
        }

        String respuestaOriginalNormalizada = normalizarTexto(this.respuestaOriginalActual);
        if (respuesta.equalsIgnoreCase(respuestaOriginalNormalizada) || this.mentirasNormalizadas.contains(respuesta.toLowerCase(Locale.ROOT))) {
            registrarError("La respuesta no es valida");
            return;
        }

        this.mentirasPorJugador.put(jugadorId, respuesta);
        this.mentirasNormalizadas.add(respuesta.toLowerCase(Locale.ROOT));

        if (this.mentirasPorJugador.size() == this.votantesEsperados().size()) {
            iniciarVotacion();
        }

        publicarEstado();
    }

    public synchronized void votarRespuestaDesdePayload(String payload) {
        if (this.fase != FasePartida.VOTANDO) {
            registrarError("Todavia no toca votar");
            return;
        }

        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_VOTAR_RESPUESTA);
        String jugadorId = leerTextoObligatorio(data, "jugadorId");
        String opcionId = leerTextoObligatorio(data, "opcionId");

        if (Objects.equals(jugadorId, this.jugadorObjetivoId)) {
            registrarError("El jugador original no vota");
            return;
        }

        if (this.votosPorJugador.containsKey(jugadorId)) {
            registrarError("Ya has votado");
            return;
        }

        if (buscarOpcion(opcionId) == null) {
            registrarError("La opcion seleccionada no existe");
            return;
        }

        this.votosPorJugador.put(jugadorId, opcionId);

        if (this.votosPorJugador.size() == votantesEsperados().size()) {
            resolverRondaActual();
        }

        publicarEstado();
    }

    public synchronized boolean revisarTransicionesAutomaticas(long ahoraMs) {
        if (this.fase == FasePartida.MOSTRANDO_RESULTADO && this.proximaTransicionEpochMs > 0 && ahoraMs >= this.proximaTransicionEpochMs) {
            if (this.indiceRondaActual + 1 < this.ordenRondas.size()) {
                this.indiceRondaActual += 1;
                prepararRondaActual();
            } else {
                finalizarPartida();
            }

            this.proximaTransicionEpochMs = 0L;
            publicarEstado();
            return true;
        }

        return false;
    }

    public synchronized HablameDeTiEstadoEnviable crearEstadoEnviable() {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("comando", COMANDO_ESTADO_PARTIDA);
        estado.put("fase", this.fase.name());
        estado.put("enCurso", this.enCurso);
        estado.put("rondaActual", this.fase == FasePartida.ESPERANDO_JUGADORES ? 0 : this.indiceRondaActual + 1);
        estado.put("totalRondas", this.ordenRondas.size());
        estado.put("mensaje", this.mensajeEstado);
        estado.put("ultimoError", this.ultimoError);
        estado.put("jugadorObjetivo", construirJugadorObjetivo());
        estado.put("preguntaDirecta", this.preguntaDirectaActual);
        estado.put("preguntaPublica", this.preguntaPublicaActual);
        estado.put("respuestaOriginal", faseMuestraRespuesta() ? this.respuestaOriginalActual : "");
        estado.put("ganadores", new ArrayList<>(this.ganadores));
        estado.put("ganadoresNombres", construirGanadoresNombres());
        estado.put("preguntasAsignadas", construirPreguntasAsignadas());
        estado.put("mentirasPendientes", construirPendientesMentiras());
        estado.put("votosPendientes", construirPendientesVotos());
        estado.put("opciones", construirOpcionesEstado());
        estado.put("resumenRonda", construirResumenRonda());
        estado.put("marcador", construirMarcador());
        estado.put("puedeEmpezar", this.fase == FasePartida.ESPERANDO_JUGADORES && this.jugadores.size() >= 2);
        estado.put("puedeResponder", this.fase == FasePartida.RESPONDIENDO_ORIGINALES);
        estado.put("puedeMentir", this.fase == FasePartida.ESCRIBIENDO_MENTIRAS);
        estado.put("puedeVotar", this.fase == FasePartida.VOTANDO);
        estado.put("puedeContinuar", this.fase == FasePartida.MOSTRANDO_RESULTADO);
        return new HablameDeTiEstadoEnviable(estado);
    }

    @Override
    public void procesarMensajeEntrante(Enviable mensaje) {
        Objects.requireNonNull(mensaje, "El mensaje entrante es obligatorio");
        throw new UnsupportedOperationException("HablameDeTi procesa entradas mediante eventos + payload JSON");
    }

    @Override
    public void iniciar() {
        this.enCurso = true;
        publicarEstado();
    }

    @Override
    public void terminar() {
        this.enCurso = false;
    }

    private void iniciarPartidaInterna() {
        this.enCurso = true;
        this.ultimoError = "";
        this.mensajeEstado = "Responde tu pregunta personal";
        this.fase = FasePartida.RESPONDIENDO_ORIGINALES;
        this.indiceRondaActual = 0;
        this.proximaTransicionEpochMs = 0L;
        this.respuestasOriginales.clear();
        this.mentirasPorJugador.clear();
        this.votosPorJugador.clear();
        this.asignaciones.clear();
        this.ordenRondas.clear();
        this.opcionesActuales.clear();
        this.resumenRondaActual.clear();
        this.mentirasNormalizadas.clear();

        List<String> ids = new ArrayList<>(this.jugadores.keySet());
        Collections.shuffle(ids, this.random);
        this.ordenRondas.addAll(ids);

        List<PreguntaBanco> preguntasMezcladas = new ArrayList<>(this.bancoPreguntas);
        Collections.shuffle(preguntasMezcladas, this.random);

        for (int i = 0; i < ids.size(); i++) {
            PreguntaBanco pregunta = preguntasMezcladas.get(i % preguntasMezcladas.size());
            this.asignaciones.put(ids.get(i), new PreguntaAsignada(pregunta, this.jugadores.get(ids.get(i)).nombreJugador));
        }

        for (JugadorPartida jugador : this.jugadores.values()) {
            jugador.puntos = 0;
        }
    }

    private void prepararRondaActual() {
        if (this.indiceRondaActual >= this.ordenRondas.size()) {
            finalizarPartida();
            return;
        }

        this.mentirasPorJugador.clear();
        this.votosPorJugador.clear();
        this.opcionesActuales.clear();
        this.resumenRondaActual.clear();
        this.mentirasNormalizadas.clear();
        this.ultimoError = "";

        this.jugadorObjetivoId = this.ordenRondas.get(this.indiceRondaActual);
        PreguntaAsignada asignacion = this.asignaciones.get(this.jugadorObjetivoId);
        this.preguntaDirectaActual = asignacion == null ? "" : asignacion.pregunta().directa();
        this.preguntaPublicaActual = asignacion == null ? "" : asignacion.preguntaPublica();
        this.respuestaOriginalActual = this.respuestasOriginales.getOrDefault(this.jugadorObjetivoId, "");
        this.mensajeEstado = "Escribe respuestas falsas para la pregunta mostrada";
        this.fase = FasePartida.ESCRIBIENDO_MENTIRAS;
    }

    private void iniciarVotacion() {
        this.opcionesActuales.clear();
        this.votosPorJugador.clear();

        String autorOriginal = this.jugadorObjetivoId;
        String nombreOriginal = this.jugadores.getOrDefault(autorOriginal, new JugadorPartida(autorOriginal, "Jugador")).nombreJugador;
        this.opcionesActuales.add(new OpcionRespuesta("ORIGINAL", autorOriginal, nombreOriginal, this.respuestaOriginalActual, true));

        for (Map.Entry<String, String> entry : this.mentirasPorJugador.entrySet()) {
            JugadorPartida jugador = this.jugadores.get(entry.getKey());
            String nombreJugador = jugador == null ? "Jugador" : jugador.nombreJugador;
            this.opcionesActuales.add(new OpcionRespuesta("MENTIRA_" + entry.getKey(), entry.getKey(), nombreJugador, entry.getValue(), false));
        }

        Collections.shuffle(this.opcionesActuales, this.random);
        this.mensajeEstado = "Elige la respuesta correcta";
        this.fase = FasePartida.VOTANDO;
    }

    private void resolverRondaActual() {
        Map<String, Integer> conteoPorOpcion = new LinkedHashMap<>();
        for (String opcionId : this.votosPorJugador.values()) {
            conteoPorOpcion.merge(opcionId, 1, Integer::sum);
        }

        this.resumenRondaActual.clear();

        for (Map.Entry<String, String> voto : this.votosPorJugador.entrySet()) {
            String opcionId = voto.getValue();
            OpcionRespuesta opcion = buscarOpcion(opcionId);
            if (opcion == null) {
                continue;
            }

            if (opcion.esOriginal) {
                JugadorPartida jugador = this.jugadores.get(voto.getKey());
                if (jugador != null) {
                    jugador.puntos += 20;
                    registrarPuntuacionSala(jugador.jugadorId, 20);
                }
            } else {
                JugadorPartida autor = this.jugadores.get(opcion.autorJugadorId);
                if (autor != null) {
                    autor.puntos += 10;
                    registrarPuntuacionSala(autor.jugadorId, 10);
                }
            }
        }

        for (OpcionRespuesta opcion : this.opcionesActuales) {
            int numSeleccionados = conteoPorOpcion.getOrDefault(opcion.opcionId, 0);
            int puntosGanadosEstaRonda = opcion.esOriginal ? numSeleccionados * 20 : numSeleccionados * 10;
            JugadorPartida autor = this.jugadores.get(opcion.autorJugadorId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("respuesta", opcion.texto);
            item.put("esOriginal", opcion.esOriginal);
            item.put("autorJugadorId", opcion.autorJugadorId);
            item.put("autorNombre", opcion.autorNombre);
            item.put("numSeleccionados", numSeleccionados);
            item.put("puntosGanadosEstaRonda", puntosGanadosEstaRonda);
            item.put("puntosTotales", autor == null ? 0 : autor.puntos);
            this.resumenRondaActual.add(item);
        }

        this.mensajeEstado = "Resultado de la ronda " + (this.indiceRondaActual + 1);
        this.fase = FasePartida.MOSTRANDO_RESULTADO;
        this.proximaTransicionEpochMs = System.currentTimeMillis() + DEMORA_RESULTADO_MS;
    }

    private void registrarError(String mensaje) {
        this.ultimoError = mensaje;
        this.mensajeEstado = mensaje;
        publicarEstado();
    }

    private void finalizarPartida() {
        if (this.fase == FasePartida.FINALIZADA) {
            return;
        }

        this.fase = FasePartida.FINALIZADA;
        this.enCurso = false;
        this.jugadorObjetivoId = null;
        this.preguntaDirectaActual = "";
        this.preguntaPublicaActual = "";
        this.respuestaOriginalActual = "";
        this.proximaTransicionEpochMs = 0L;

        int mejorPuntuacion = this.jugadores.values().stream()
                .mapToInt(jugador -> jugador.puntos)
                .max()
                .orElse(0);

        this.ganadores.clear();
        this.ganadores.addAll(this.jugadores.values().stream()
                .filter(jugador -> jugador.puntos == mejorPuntuacion)
                .map(jugador -> jugador.jugadorId)
                .toList());

        for (String ganadorId : this.ganadores) {
            this.salaService.incrementarVictoria(this.salaId, ganadorId);
        }

        if (this.ganadores.size() == 1) {
            String ganadorId = this.ganadores.iterator().next();
            JugadorPartida ganador = this.jugadores.get(ganadorId);
            this.mensajeEstado = ganador == null ? "Partida finalizada" : "Ganador: " + ganador.nombreJugador;
        } else if (!this.ganadores.isEmpty()) {
            this.mensajeEstado = "Empate entre varios jugadores";
        } else {
            this.mensajeEstado = "Partida finalizada";
        }

        this.ultimoError = "";
    }

    private List<String> construirGanadoresNombres() {
        return this.ganadores.stream()
                .map(this.jugadores::get)
                .filter(Objects::nonNull)
                .map(jugador -> jugador.nombreJugador)
                .toList();
    }

    private void registrarPuntuacionSala(String jugadorId, int puntos) {
        if (jugadorId == null || jugadorId.isBlank() || puntos == 0) {
            return;
        }

        this.salaService.incrementarPuntuacion(this.salaId, jugadorId, puntos);
    }

    private void publicarEstado() {
        HablameDeTiEstadoEnviable estado = crearEstadoEnviable();
        traductorJugadores().enviar(estado);
        traductorPantalla().enviar(estado);
    }

    private Traductor<String> traductorJugadores() {
        @SuppressWarnings("unchecked")
        Traductor<String> traductor = (Traductor<String>) this.conexionJugadores;
        return traductor;
    }

    private Traductor<String> traductorPantalla() {
        @SuppressWarnings("unchecked")
        Traductor<String> traductor = (Traductor<String>) this.conexionPantalla;
        return traductor;
    }

    private Map<String, Object> construirJugadorObjetivo() {
        if (this.jugadorObjetivoId == null) {
            return Map.of();
        }

        JugadorPartida jugador = this.jugadores.get(this.jugadorObjetivoId);
        if (jugador == null) {
            return Map.of();
        }

        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("jugadorId", jugador.jugadorId);
        mapa.put("nombreJugador", jugador.nombreJugador);
        return mapa;
    }

    private List<Map<String, Object>> construirPreguntasAsignadas() {
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Map.Entry<String, PreguntaAsignada> entry : this.asignaciones.entrySet()) {
            PreguntaAsignada asignada = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jugadorId", entry.getKey());
            item.put("nombreJugador", asignada.nombreJugador());
            item.put("preguntaDirecta", asignada.pregunta().directa());
            item.put("preguntaPublica", asignada.preguntaPublica());
            item.put("respondida", this.respuestasOriginales.containsKey(entry.getKey()));
            item.put("respuestaOriginal", this.respuestasOriginales.getOrDefault(entry.getKey(), ""));
            resultado.add(item);
        }

        return resultado;
    }

    private List<String> construirPendientesMentiras() {
        Set<String> pendientes = new LinkedHashSet<>(this.jugadores.keySet());
        pendientes.remove(this.jugadorObjetivoId);
        pendientes.removeAll(this.mentirasPorJugador.keySet());
        return new ArrayList<>(pendientes);
    }

    private List<String> construirPendientesVotos() {
        Set<String> pendientes = new LinkedHashSet<>(this.jugadores.keySet());
        pendientes.remove(this.jugadorObjetivoId);
        pendientes.removeAll(this.votosPorJugador.keySet());
        return new ArrayList<>(pendientes);
    }

    private List<Map<String, Object>> construirOpcionesEstado() {
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (OpcionRespuesta opcion : this.opcionesActuales) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("opcionId", opcion.opcionId);
            item.put("autorJugadorId", opcion.autorJugadorId);
            item.put("autorNombre", opcion.autorNombre);
            item.put("texto", opcion.texto);
            item.put("esOriginal", opcion.esOriginal);
            item.put("seleccionable", this.fase == FasePartida.VOTANDO);
            resultado.add(item);
        }

        return resultado;
    }

    private List<Map<String, Object>> construirMarcador() {
        return this.jugadores.values().stream()
                .sorted(Comparator.comparingInt((JugadorPartida jugador) -> jugador.puntos).reversed()
                        .thenComparing(jugador -> jugador.nombreJugador))
                .map(jugador -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("jugadorId", jugador.jugadorId);
                    item.put("nombreJugador", jugador.nombreJugador);
                    item.put("puntos", jugador.puntos);
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> construirResumenRonda() {
        return new ArrayList<>(this.resumenRondaActual);
    }

    private OpcionRespuesta buscarOpcion(String opcionId) {
        for (OpcionRespuesta opcion : this.opcionesActuales) {
            if (opcion.opcionId.equals(opcionId)) {
                return opcion;
            }
        }

        return null;
    }

    private boolean faseMuestraRespuesta() {
        return this.fase == FasePartida.VOTANDO || this.fase == FasePartida.MOSTRANDO_RESULTADO || this.fase == FasePartida.FINALIZADA;
    }

    private Set<String> votantesEsperados() {
        Set<String> resultado = new LinkedHashSet<>(this.jugadores.keySet());
        resultado.remove(this.jugadorObjetivoId);
        return resultado;
    }

    private Map<String, Object> leerPayloadComoMapa(String payload, String comandoEsperado) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("El payload de " + comandoEsperado + " no puede estar vacio");
        }

        try {
            Map<String, Object> mapa = OBJECT_MAPPER.readValue(payload, MAP_TYPE);
            if (mapa == null) {
                throw new IllegalArgumentException("No se pudo interpretar el payload de " + comandoEsperado);
            }

            return mapa;
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("No se pudo interpretar el payload de " + comandoEsperado, error);
        }
    }

    private String leerTextoObligatorio(Map<String, Object> data, String campo) {
        String texto = leerTextoOpcional(data, campo);
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio");
        }

        return texto;
    }

    private String leerTextoOpcional(Map<String, Object> data, String campo) {
        Object valor = data.get(campo);
        if (valor == null) {
            return null;
        }

        String texto = String.valueOf(valor).trim();
        return texto.isEmpty() ? null : texto;
    }

    private String normalizarTexto(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private List<PreguntaBanco> cargarBancoPreguntas() {
        List<PreguntaBanco> preguntas = new ArrayList<>();

        try (InputStream input = getClass().getClassLoader().getResourceAsStream("preguntas/hablame_de_ti.txt")) {
            if (input == null) {
                return preguntasPorDefecto();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    String texto = linea.trim();
                    if (texto.isEmpty() || texto.startsWith("#")) {
                        continue;
                    }

                    PreguntaBanco pregunta = PreguntaBanco.fromLine(texto);
                    if (pregunta != null) {
                        preguntas.add(pregunta);
                    }
                }
            }
        } catch (Exception _error) {
            preguntas.clear();
        }

        if (preguntas.isEmpty()) {
            preguntas.addAll(preguntasPorDefecto());
        }

        return preguntas;
    }

    private List<PreguntaBanco> preguntasPorDefecto() {
        return List.of(
                new PreguntaBanco("¿Qué canción no puedes dejar de escuchar?", "¿Qué canción no puede dejar de escuchar el jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Cuál es tu experiencia más paranormal?", "¿Cuál es la experiencia más paranormal del jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Qué comida te representa más?", "¿Qué comida representa mejor al jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Qué plan te hace feliz sin gastar mucho?", "¿Qué plan feliz y barato prefiere el jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Qué objeto siempre llevarías a una isla desierta?", "¿Qué objeto llevaría siempre el jugador [NOMBRE_JUGADOR] a una isla desierta?") ,
                new PreguntaBanco("¿Qué serie volverías a ver entera?", "¿Qué serie volvería a ver entera el jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Qué hobby te gustaría dominar?", "¿Qué hobby le gustaría dominar al jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Qué villano te parece irresistible?", "¿Qué villano le parece irresistible al jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Qué lugar te da paz instantánea?", "¿Qué lugar le da paz instantánea al jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Qué videojuego te define mejor?", "¿Qué videojuego define mejor al jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Qué objeto de tu infancia no olvidarías?", "¿Qué objeto de la infancia no olvidaría el jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Qué talento oculto tienes?", "¿Qué talento oculto tiene el jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Qué libro recomendarías siempre?", "¿Qué libro recomendaría siempre el jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Qué olor te trae un recuerdo fuerte?", "¿Qué olor le trae un recuerdo fuerte al jugador [NOMBRE_JUGADOR]?") ,
                new PreguntaBanco("¿Qué accesorio usarías a diario?", "¿Qué accesorio usaría a diario el jugador [NOMBRE_JUGADOR]?")
        );
    }

    private enum FasePartida {
        ESPERANDO_JUGADORES,
        RESPONDIENDO_ORIGINALES,
        ESCRIBIENDO_MENTIRAS,
        VOTANDO,
        MOSTRANDO_RESULTADO,
        FINALIZADA
    }

    private static final class JugadorPartida {
        private final String jugadorId;
        private String nombreJugador;
        private int puntos;

        private JugadorPartida(String jugadorId, String nombreJugador) {
            this.jugadorId = jugadorId;
            this.nombreJugador = nombreJugador;
        }
    }

    private record PreguntaBanco(String directa, String publica) {
        private static PreguntaBanco fromLine(String linea) {
            String[] partes = linea.split("\\|\\|", 2);
            if (partes.length == 1) {
                return new PreguntaBanco(partes[0].trim(), partes[0].trim());
            }

            return new PreguntaBanco(partes[0].trim(), partes[1].trim());
        }
    }

    private record PreguntaAsignada(PreguntaBanco pregunta, String nombreJugador) {
        private String preguntaPublica() {
            return pregunta.publica().replace("[NOMBRE_JUGADOR]", nombreJugador);
        }
    }

    private record OpcionRespuesta(String opcionId, String autorJugadorId, String autorNombre, String texto, boolean esOriginal) {
    }
}