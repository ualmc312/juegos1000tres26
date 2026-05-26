package com.juegos1000tres.juegos1000tres_backend.juegos.AdivinaElPersonaje;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.DestinoEnvio;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Recibo;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.ia.RespuestaIA;
import com.juegos1000tres.juegos1000tres_backend.ia.ServicioIA;
import com.juegos1000tres.juegos1000tres_backend.ia.SolicitudIA;
import com.juegos1000tres.juegos1000tres_backend.modelos.Juego;
import com.juegos1000tres.juegos1000tres_backend.sala.SalaService;

public class AdivinaElPersonajeJuego extends Juego {

    public static final String COMANDO_REGISTRAR_JUGADOR = "REGISTRAR_JUGADOR";
    public static final String COMANDO_PROPONER_TEMA = "PROPONER_TEMA";
    public static final String COMANDO_HACER_PREGUNTA = "HACER_PREGUNTA";
    public static final String COMANDO_INTENTAR_ADIVINAR = "INTENTAR_ADIVINAR";
    public static final String COMANDO_ESTADO_PARTIDA = "ESTADO_ADIVINA_PERSONAJE";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ServicioIA servicioIA;
    private final com.juegos1000tres.juegos1000tres_backend.juegos.common.TemaSelector temaSelector;
    private final SalaService salaService;
    private final String salaId;
    private final Map<String, JugadorPartida> jugadores = new LinkedHashMap<>();
    private final List<InteraccionPartida> historial = new ArrayList<>();
    private final Set<String> ganadores = new LinkedHashSet<>();

    private FasePartida fase = FasePartida.ESPERANDO_TEMA;
    private boolean enCurso;
    private String tema = "";
    private String personajeSecreto = "";
    private String descripcionPersonaje = "";
    private String jugadorTemaId;
    private String mensajeEstado = "Escribe un tema para comenzar";
    private String ultimaRespuestaIA = "";

    public AdivinaElPersonajeJuego(Traductor<?> conexionJugadores, Traductor<?> conexionPantalla, ServicioIA servicioIA, com.juegos1000tres.juegos1000tres_backend.juegos.common.TemaSelector temaSelector, SalaService salaService, String salaId) {
        super(100, true, conexionJugadores, conexionPantalla);
        this.servicioIA = Objects.requireNonNull(servicioIA, "El servicio de IA es obligatorio");
        this.temaSelector = Objects.requireNonNull(temaSelector, "TemaSelector es obligatorio");
        this.salaService = Objects.requireNonNull(salaService, "SalaService es obligatorio");
        this.salaId = Objects.requireNonNull(salaId, "SalaId es obligatorio");
    }

    public Recibo<String> registrarEventosEnRecibo(Recibo<String> reciboBase) {
        Objects.requireNonNull(reciboBase, "El recibo base es obligatorio");

        return reciboBase
                .conEvento(COMANDO_REGISTRAR_JUGADOR, new RegistrarJugadorEvento(this))
                .conEvento(COMANDO_PROPONER_TEMA, new ProponerTemaEvento(this))
                .conEvento(COMANDO_HACER_PREGUNTA, new HacerPreguntaEvento(this))
                .conEvento(COMANDO_INTENTAR_ADIVINAR, new IntentarAdivinarEvento(this));
    }

    public synchronized void registrarJugadorDesdePayload(String payload) {
        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_REGISTRAR_JUGADOR);
        String jugadorIdOriginal = leerTextoObligatorio(data, "jugadorId");
        String nombreJugador = leerTextoOpcional(data, "nombreJugador");
        String jugadorId = normalizarJugadorId(jugadorIdOriginal, nombreJugador);

        JugadorPartida jugador = this.jugadores.computeIfAbsent(jugadorId,
                id -> new JugadorPartida(id, nombreJugador == null ? "Jugador" : nombreJugador));
        if (nombreJugador != null && !nombreJugador.isBlank()) {
            jugador.nombreJugador = nombreJugador.trim();
        }

        seleccionarJugadorTemaSiHaceFalta();

        enviarEstadoActualizado();
    }

    public synchronized void proponerTemaDesdePayload(String payload) {
        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_PROPONER_TEMA);
        String jugadorIdOriginal = leerTextoObligatorio(data, "jugadorId");
        String nombreJugador = leerTextoOpcional(data, "nombreJugador");
        String jugadorId = normalizarJugadorId(jugadorIdOriginal, nombreJugador);
        String temaPropuesto = leerTextoObligatorio(data, "tema");

        registrarJugadorSiFalta(jugadorId, nombreJugador);

        if (this.jugadorTemaId != null && !Objects.equals(this.jugadorTemaId, jugadorId)) {
            limpiarEstadosPrivadosJugadores();
            this.mensajeEstado = "Solo el jugador seleccionado puede proponer el tema";
            this.ultimaRespuestaIA = "NO_AUTORIZADO";
            enviarEstadoActualizado();
            return;
        }

        if (this.fase == FasePartida.JUGANDO) {
            limpiarEstadosPrivadosJugadores();
            this.mensajeEstado = "La partida ya tiene un tema activo";
            this.ultimaRespuestaIA = "TEMA_YA_ACTIVO";
            enviarEstadoActualizado();
            return;
        }

        limpiarEstadosPrivadosJugadores();
        this.fase = FasePartida.VALIDANDO_TEMA;
        this.mensajeEstado = "Validando tema con IA...";
        this.ultimaRespuestaIA = "VALIDANDO_TEMA";
        enviarEstadoActualizado();

        ValidacionTema validacion;
        try {
            validacion = validarTemaConIA(temaPropuesto);
        } catch (RuntimeException error) {
            limpiarEstadosPrivadosJugadores();
            this.fase = FasePartida.ESPERANDO_TEMA;
            this.mensajeEstado = "No se pudo validar el tema. Intenta otro";
            this.ultimaRespuestaIA = error.getMessage() == null ? "ERROR_VALIDANDO_TEMA" : error.getMessage();
            enviarEstadoActualizado();
            return;
        }

        if (!validacion.valido) {
            limpiarEstadosPrivadosJugadores();
            this.fase = FasePartida.ESPERANDO_TEMA;
            this.tema = "";
            this.personajeSecreto = "";
            this.descripcionPersonaje = "";
            this.mensajeEstado = validacion.mensaje;
            this.ultimaRespuestaIA = validacion.raw;
            enviarEstadoActualizado();
            return;
        }

        limpiarEstadosPrivadosJugadores();
        this.tema = validacion.temaNormalizado;
        this.jugadorTemaId = jugadorId;
        SeleccionPersonaje seleccion = seleccionarPersonajeConIA(this.tema);
        this.personajeSecreto = seleccion.personaje;
        this.descripcionPersonaje = seleccion.descripcion;
        this.fase = FasePartida.JUGANDO;
        this.enCurso = true;
        this.mensajeEstado = "Tema aceptado. Empieza a preguntar";
        this.ultimaRespuestaIA = "TEMA_VALIDO";
        this.historial.add(InteraccionPartida.tema(this.tema, jugadorId));
        enviarEstadoActualizado();
    }

    public synchronized void hacerPreguntaDesdePayload(String payload) {
        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_HACER_PREGUNTA);
        String jugadorIdOriginal = leerTextoObligatorio(data, "jugadorId");
        String nombreJugador = leerTextoOpcional(data, "nombreJugador");
        String jugadorId = normalizarJugadorId(jugadorIdOriginal, nombreJugador);
        String pregunta = leerTextoObligatorio(data, "pregunta");

        registrarJugadorSiFalta(jugadorId, nombreJugador);

        JugadorPartida jugador = this.jugadores.get(jugadorId);

        if (this.fase != FasePartida.JUGANDO) {
            if (jugador != null) {
                jugador.mensajeEstado = "Todavia no hay un personaje para adivinar";
                jugador.ultimaRespuestaIA = "";
            }
            enviarEstadoActualizado();
            return;
        }

        if (jugador == null || jugador.acertado) {
            enviarEstadoActualizado();
            return;
        }

        RespuestaPregunta respuesta = responderPreguntaConIA(pregunta);
        jugador.ultimaRespuestaIA = respuesta.codigo;

        if (respuesta.valida) {
            jugador.preguntasValidas += 1;
            jugador.mensajeEstado = "Respuesta: " + respuesta.codigo;
        } else {
            jugador.preguntasInvalidas += 1;
            jugador.mensajeEstado = respuesta.mensaje;
        }

        this.historial.add(InteraccionPartida.pregunta(jugadorId, pregunta, respuesta.codigo, respuesta.valida));
        enviarEstadoActualizado();
    }

    public synchronized void intentarAdivinarDesdePayload(String payload) {
        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_INTENTAR_ADIVINAR);
        String jugadorIdOriginal = leerTextoObligatorio(data, "jugadorId");
        String nombreJugador = leerTextoOpcional(data, "nombreJugador");
        String jugadorId = normalizarJugadorId(jugadorIdOriginal, nombreJugador);
        String intento = leerTextoObligatorio(data, "intento");

        registrarJugadorSiFalta(jugadorId, nombreJugador);

        JugadorPartida jugador = this.jugadores.get(jugadorId);

        if (this.fase != FasePartida.JUGANDO) {
            if (jugador != null) {
                jugador.mensajeEstado = "La partida aun no esta lista";
                jugador.ultimaRespuestaIA = "";
            }
            enviarEstadoActualizado();
            return;
        }

        if (jugador == null || jugador.acertado) {
            enviarEstadoActualizado();
            return;
        }

        VerificacionAdivinanza verificacion = verificarAdivinanzaConIA(intento);

        if (verificacion.acierta) {
            jugador.acertado = true;
            jugador.preguntasAlAcertar = jugador.preguntasValidas;
            this.ganadores.add(jugadorId);
            
            // Limpiar solo el mensajeEstado para todos los jugadores para que vean el anuncio global,
            // pero NO su ultimaRespuestaIA para evitar filtraciones de la solucion.
            for (JugadorPartida j : this.jugadores.values()) {
                j.mensajeEstado = "";
            }
            this.mensajeEstado = jugador.nombreJugador + " ha adivinado el personaje";
            this.ultimaRespuestaIA = ""; // Vaciar a nivel global para que otros no la hereden
            
            // Asignar el mensaje y respuesta de éxito privados al ganador
            jugador.mensajeEstado = "¡Correcto! Has adivinado el personaje";
            jugador.ultimaRespuestaIA = verificacion.raw;
        } else {
            jugador.mensajeEstado = "Sigue intentandolo";
            jugador.ultimaRespuestaIA = "";
        }

        this.historial.add(InteraccionPartida.adivinanza(jugadorId, intento, verificacion.acierta));
        comprobarFinalizacion();
        enviarEstadoActualizado();
    }

    public synchronized AdivinaElPersonajeEstadoEnviable crearEstadoEnviable() {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("comando", COMANDO_ESTADO_PARTIDA);
        estado.put("fase", this.fase.name());
        estado.put("enCurso", this.enCurso);
        estado.put("tema", this.tema);
        estado.put("mensaje", this.mensajeEstado);
        estado.put("ultimaRespuestaIA", this.ultimaRespuestaIA);
        estado.put("jugadorTemaId", this.jugadorTemaId);
        estado.put("personajeRevelado", this.fase == FasePartida.FINALIZADA ? this.personajeSecreto : "");
        estado.put("descripcionPersonaje", this.fase == FasePartida.FINALIZADA ? this.descripcionPersonaje : "");
        estado.put("ganadores", new ArrayList<>(this.ganadores));
        estado.put("ganadoresNombres", construirGanadoresNombres());
        estado.put("jugadores", construirJugadoresEstado());
        estado.put("historial", construirHistorialEstado());
        estado.put("puedeProponerTema", this.fase == FasePartida.ESPERANDO_TEMA);
        estado.put("puedePreguntar", this.fase == FasePartida.JUGANDO);
        estado.put("puedeAdivinar", this.fase == FasePartida.JUGANDO);
        return new AdivinaElPersonajeEstadoEnviable(estado);
    }

    public synchronized AdivinaElPersonajeEstadoEnviable crearEstadoPantallaEnviable() {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("comando", COMANDO_ESTADO_PARTIDA);
        estado.put("fase", this.fase.name());
        estado.put("enCurso", this.enCurso);
        estado.put("tema", this.tema);
        estado.put("mensaje", this.mensajeEstado);
        estado.put("jugadorTemaId", this.jugadorTemaId);
        estado.put("personajeRevelado", this.fase == FasePartida.FINALIZADA ? this.personajeSecreto : "");
        estado.put("ganadores", new ArrayList<>(this.ganadores));
        estado.put("ganadoresNombres", construirGanadoresNombres());
        estado.put("jugadores", construirJugadoresEstado());
        // No incluir historial en pantalla compartida, solo scoreboard
        estado.put("puedeProponerTema", this.fase == FasePartida.ESPERANDO_TEMA);
        return new AdivinaElPersonajeEstadoEnviable(estado);
    }

    @Override
    public void procesarMensajeEntrante(Enviable mensaje) {
        Objects.requireNonNull(mensaje, "El mensaje entrante es obligatorio");
        throw new UnsupportedOperationException("AdivinaElPersonaje procesa entradas mediante eventos + payload JSON");
    }

    @Override
    public void iniciar() {
        this.enCurso = true;
        limpiarEstadosPrivadosJugadores();
        seleccionarJugadorTemaSiHaceFalta();
        enviarEstadoActualizado();
    }

    private void seleccionarJugadorTemaSiHaceFalta() {
        if (!this.enCurso || this.jugadorTemaId != null || this.jugadores.size() < 2) {
            if (this.enCurso && this.jugadorTemaId == null && this.jugadores.size() < 2) {
                this.mensajeEstado = "Esperando al menos 2 jugadores";
            }

            return;
        }

        List<String> ids = new ArrayList<>(this.jugadores.keySet());
        Collections.shuffle(ids);
        this.jugadorTemaId = ids.get(0);
        this.mensajeEstado = "Jugador elegido para proponer tema: " + this.jugadores.get(this.jugadorTemaId).nombreJugador;
        this.ultimaRespuestaIA = "JUGADOR_TEMA_SELECCIONADO";
    }

    @Override
    public void terminar() {
        this.enCurso = false;
    }

    private void comprobarFinalizacion() {
        if (this.jugadores.isEmpty() || this.fase == FasePartida.FINALIZADA) {
            return;
        }

        boolean todosAcertaron = this.jugadores.values().stream().allMatch(jugador -> jugador.acertado);
        if (!todosAcertaron) {
            return;
        }

        this.fase = FasePartida.FINALIZADA;
        this.enCurso = false;
        int minimo = this.jugadores.values().stream().mapToInt(jugador -> jugador.preguntasAlAcertar).min().orElse(0);

        this.ganadores.clear();
        this.ganadores.addAll(this.jugadores.values().stream()
                .filter(jugador -> jugador.preguntasAlAcertar == minimo)
                .map(jugador -> jugador.jugadorId)
                .toList());

        for (String ganadorId : this.ganadores) {
            this.salaService.incrementarVictoria(this.salaId, ganadorId);
        }

        limpiarEstadosPrivadosJugadores();
        if (this.ganadores.size() == 1) {
            String ganadorId = this.ganadores.iterator().next();
            this.mensajeEstado = "Ganador: " + this.jugadores.get(ganadorId).nombreJugador;
        } else {
            this.mensajeEstado = "Empate entre varios jugadores";
        }
        this.ultimaRespuestaIA = "PARTIDA_FINALIZADA";
    }

    private void limpiarEstadosPrivadosJugadores() {
        for (JugadorPartida j : this.jugadores.values()) {
            j.mensajeEstado = "";
            j.ultimaRespuestaIA = "";
        }
    }

    private List<String> construirGanadoresNombres() {
        return this.ganadores.stream()
                .map(this.jugadores::get)
                .filter(Objects::nonNull)
                .map(jugador -> jugador.nombreJugador)
                .toList();
    }

    private void registrarJugadorSiFalta(String jugadorId, String nombreJugador) {
        this.jugadores.computeIfAbsent(jugadorId,
                id -> new JugadorPartida(id, nombreJugador == null || nombreJugador.isBlank() ? "Jugador" : nombreJugador.trim()));
    }

    private String normalizarJugadorId(String jugadorId, String nombreJugador) {
        try {
            return this.salaService.normalizarJugadorId(this.salaId, jugadorId, nombreJugador);
        } catch (RuntimeException ex) {
            // Si no se puede mapear, conserva el id original para no romper la sesion WS.
            return jugadorId;
        }
    }

    private ValidacionTema validarTemaConIA(String temaPropuesto) {
        String promptTemplate = """
            Analiza este tema para el juego 'Adivina el personaje'.
            Devuelve SOLO JSON valido con las claves: valido(boolean), temaNormalizado(string), mensaje(string).
            Considera valido un tema cuando, a partir de él, pueda seleccionarse al menos un personaje concreto y reconocible que pueda ser adivinado mediante preguntas de sí/no.
            Acepta títulos de obras (series, películas, libros) o cosas de las que puedan sacarse como historia española o poetas españoles. Si el tema es el título de una obra, considera valido si la obra contiene personajes reconocibles; en ese caso normaliza el tema al título de la obra.
            Si no es valido, explica brevemente por qué y sugiere un tipo de tema alternativo (por ejemplo:  'título de obra con personajes', 'categoría concreta').

            Tema: %s
            """;

        com.juegos1000tres.juegos1000tres_backend.juegos.common.TemaSelector.ValidacionTema t = this.temaSelector.validarTema(temaPropuesto, promptTemplate,
            "Eres un validador estricto de temas para un juego de adivinar personajes. Responde solo JSON.",
            Map.of("juego", "adivina-el-personaje"));

        return new ValidacionTema(t.valido(), t.temaNormalizado(), t.mensaje(), t.raw());
    }

    private SeleccionPersonaje seleccionarPersonajeConIA(String tema) {
        String prompt = """
            Elige un solo personaje concreto y reconocible del tema indicado para un juego de adivinar personaje.
            Devuelve SOLO JSON valido con las claves: personaje(string), descripcion(string).
            No reveles razonamientos adicionales.

            Tema: %s
            """.formatted(tema);

        JsonNode json = consultarJson(prompt,
                "Eres un generador de personajes para un juego de preguntas de si o no. Responde solo JSON.");

        String personaje = leerTextoJson(json, "personaje", tema);
        String descripcion = leerTextoJson(json, "descripcion", "Personaje de la tematica indicada");
        return new SeleccionPersonaje(personaje, descripcion);
    }

    private RespuestaPregunta responderPreguntaConIA(String pregunta) {
        String prompt = "Tema: " + this.tema + "\n"
                + "Personaje secreto: " + this.personajeSecreto + "\n"
                + "Descripcion: " + this.descripcionPersonaje + "\n"
                + "Pregunta del jugador: " + pregunta + "\n\n"
                + "Responde SOLO JSON valido con las claves: respuesta(string) y mensaje(string).\n"
                + "La respuesta debe ser una de estas opciones exactas: SI, NO, MAYORMENTE_SI, MAYORMENTE_NO, INVALIDA.\n"
                + "Usa INVALIDA si la pregunta no puede contestarse con informacion util de si/no.";

        JsonNode json = consultarJson(prompt,
                "Eres un arbitro de preguntas de si o no. Responde solo JSON valido.");

        String codigo = leerTextoJson(json, "respuesta", "INVALIDA").toUpperCase(Locale.ROOT).trim();
        if (!esCodigoValidoRespuesta(codigo)) {
            codigo = "INVALIDA";
        }

        String mensaje = leerTextoJson(json, "mensaje", "Respuesta procesada");
        return new RespuestaPregunta(codigo, mensaje, !"INVALIDA".equals(codigo), json.toString());
    }

    private VerificacionAdivinanza verificarAdivinanzaConIA(String intento) {
        String prompt = "Tema: " + this.tema + "\n"
                + "Personaje secreto: " + this.personajeSecreto + "\n"
                + "Descripcion: " + this.descripcionPersonaje + "\n"
                + "Adivinanza del jugador: " + intento + "\n\n"
                + "Responde SOLO JSON valido con las claves: acierta(boolean) y mensaje(string).\n"
                + "Decide si el intento identifica al mismo personaje o a una variante claramente equivalente.";

        JsonNode json = consultarJson(prompt,
                "Eres un verificador de respuestas para un juego de adivinar personaje. Responde solo JSON.");

        boolean acierta = leerBooleano(json, "acierta", false);
        String mensaje = leerTextoJson(json, "mensaje", acierta ? "Correcto" : "Incorrecto");
        return new VerificacionAdivinanza(acierta, mensaje, json.toString());
    }

    private JsonNode consultarJson(String prompt, String instruccionesSistema) {
        RespuestaIA respuesta = this.servicioIA.consultar(SolicitudIA.completa(
                prompt,
                instruccionesSistema,
                null,
                0.2d,
                512,
                Map.of("juego", "adivina-el-personaje")));

        String texto = limpiarRespuestaJson(respuesta.texto());
        try {
            return OBJECT_MAPPER.readTree(texto);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("La IA no devolvio JSON valido", error);
        }
    }

    private String limpiarRespuestaJson(String texto) {
        if (texto == null) {
            throw new IllegalStateException("La IA no devolvio contenido");
        }

        String limpio = texto.trim();
        if (limpio.startsWith("```")) {
            limpio = limpio.replaceFirst("^```(?:json)?\\s*", "");
            limpio = limpio.replaceFirst("\\s*```$", "");
        }

        int inicio = limpio.indexOf('{');
        int fin = limpio.lastIndexOf('}');
        if (inicio >= 0 && fin > inicio) {
            return limpio.substring(inicio, fin + 1);
        }

        return limpio;
    }

    private boolean leerBooleano(JsonNode json, String campo, boolean valorPorDefecto) {
        if (json == null || !json.has(campo)) {
            return valorPorDefecto;
        }

        JsonNode nodo = json.get(campo);
        if (nodo.isBoolean()) {
            return nodo.asBoolean();
        }

        String texto = nodo.asText("").trim().toLowerCase(Locale.ROOT);
        return switch (texto) {
            case "true", "si", "sí", "1" -> true;
            case "false", "no", "0" -> false;
            default -> valorPorDefecto;
        };
    }

    private String leerTextoJson(JsonNode json, String campo, String valorPorDefecto) {
        if (json == null || !json.has(campo)) {
            return valorPorDefecto;
        }

        String texto = json.get(campo).asText(valorPorDefecto);
        return texto == null ? valorPorDefecto : texto.trim();
    }

    private boolean esCodigoValidoRespuesta(String codigo) {
        return "SI".equals(codigo)
                || "NO".equals(codigo)
                || "MAYORMENTE_SI".equals(codigo)
                || "MAYORMENTE_NO".equals(codigo)
                || "INVALIDA".equals(codigo);
    }

    private List<Map<String, Object>> construirJugadoresEstado() {
        return this.jugadores.values().stream()
                .map(jugador -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("jugadorId", jugador.jugadorId);
                    item.put("nombreJugador", jugador.nombreJugador);
                    item.put("preguntasValidas", jugador.preguntasValidas);
                    item.put("preguntasInvalidas", jugador.preguntasInvalidas);
                    item.put("preguntasAlAcertar", jugador.preguntasAlAcertar);
                    item.put("acertado", jugador.acertado);
                    return item;
                })
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("nombreJugador"))))
                .toList();
    }

    private List<Map<String, Object>> construirHistorialEstado() {
        return this.historial.stream().map(InteraccionPartida::toMap).toList();
    }

    private void enviarEstadoActualizado() {
        AdivinaElPersonajeEstadoEnviable estadoJugadores = crearEstadoEnviable();
        AdivinaElPersonajeEstadoEnviable estadoPantalla = crearEstadoPantallaEnviable();

        Traductor<String> trad = traductorJugadores();
        // Enviar estado privado a cada jugador (filtrado por su jugadorId)
        try {
            String payloadBase = trad.traducirEnviableAFormato(estadoJugadores);
            JsonNode root = OBJECT_MAPPER.readTree(payloadBase);

            for (String jugadorId : this.jugadores.keySet()) {
                com.fasterxml.jackson.databind.node.ObjectNode copia = OBJECT_MAPPER.createObjectNode();
                copia.setAll((com.fasterxml.jackson.databind.node.ObjectNode) root);

                // filtrar historial por jugadorId
                com.fasterxml.jackson.databind.node.ArrayNode historialFiltrado = OBJECT_MAPPER.createArrayNode();
                JsonNode nodoHist = root.get("historial");
                if (nodoHist != null && nodoHist.isArray()) {
                    for (JsonNode item : nodoHist) {
                        if (item.has("jugadorId") && jugadorId.equals(item.get("jugadorId").asText(""))) {
                            historialFiltrado.add(item);
                        }
                    }
                }

                copia.set("historial", historialFiltrado);

                // Sobrescribir el mensaje y última respuesta para este jugador si tiene valores específicos
                JugadorPartida jug = this.jugadores.get(jugadorId);
                if (jug != null) {
                    if (jug.mensajeEstado != null && !jug.mensajeEstado.isEmpty()) {
                        copia.put("mensaje", jug.mensajeEstado);
                    }
                    if (jug.ultimaRespuestaIA != null && !jug.ultimaRespuestaIA.isEmpty()) {
                        copia.put("ultimaRespuestaIA", jug.ultimaRespuestaIA);
                    }
                }

                trad.enviar(
                        new AdivinaElPersonajeEstadoEnviable(OBJECT_MAPPER.convertValue(copia, new TypeReference<Map<String, Object>>() {})),
                        DestinoEnvio.jugador(jugadorId));
            }
        } catch (JsonProcessingException | RuntimeException ex) {
            // en caso de error, enviar estado completo a todos
            trad.enviar(estadoJugadores);
        }

        // enviar estado de pantalla (sin historial)
        traductorPantalla().enviar(estadoPantalla, DestinoEnvio.pantalla());
    }

    @SuppressWarnings("unchecked")
    private Traductor<String> traductorJugadores() {
        return (Traductor<String>) this.conexionJugadores;
    }

    @SuppressWarnings("unchecked")
    private Traductor<String> traductorPantalla() {
        return (Traductor<String>) this.conexionPantalla;
    }

    private Map<String, Object> leerPayloadComoMapa(String payload, String comandoEsperado) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("El payload de " + comandoEsperado + " no puede estar vacio");
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("El payload de " + comandoEsperado + " debe ser un objeto JSON");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> mapa = OBJECT_MAPPER.convertValue(root, Map.class);
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

    private enum FasePartida {
        ESPERANDO_TEMA,
        VALIDANDO_TEMA,
        JUGANDO,
        FINALIZADA
    }

    private static final class JugadorPartida {
        private final String jugadorId;
        private String nombreJugador;
        private int preguntasValidas;
        private int preguntasInvalidas;
        private int preguntasAlAcertar;
        private boolean acertado;
        private String mensajeEstado = "";
        private String ultimaRespuestaIA = "";

        private JugadorPartida(String jugadorId, String nombreJugador) {
            this.jugadorId = jugadorId;
            this.nombreJugador = nombreJugador;
        }
    }

    private static final class InteraccionPartida {
        private final String tipo;
        private final String jugadorId;
        private final String contenido;
        private final String respuestaIA;
        private final boolean valida;

        private InteraccionPartida(String tipo, String jugadorId, String contenido, String respuestaIA, boolean valida) {
            this.tipo = tipo;
            this.jugadorId = jugadorId;
            this.contenido = contenido;
            this.respuestaIA = respuestaIA;
            this.valida = valida;
        }

        private static InteraccionPartida tema(String tema, String jugadorId) {
            return new InteraccionPartida("TEMA", jugadorId, tema, "", true);
        }

        private static InteraccionPartida pregunta(String jugadorId, String pregunta, String respuestaIA, boolean valida) {
            return new InteraccionPartida("PREGUNTA", jugadorId, pregunta, respuestaIA, valida);
        }

        private static InteraccionPartida adivinanza(String jugadorId, String intento, boolean acierto) {
            return new InteraccionPartida("ADIVINANZA", jugadorId, intento, acierto ? "ACIERTO" : "FALLO", acierto);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> mapa = new LinkedHashMap<>();
            mapa.put("tipo", this.tipo);
            mapa.put("jugadorId", this.jugadorId);
            mapa.put("contenido", this.contenido);
            mapa.put("respuestaIA", this.respuestaIA);
            mapa.put("valida", this.valida);
            return mapa;
        }
    }

    private record ValidacionTema(boolean valido, String temaNormalizado, String mensaje, String raw) {
    }

    private record SeleccionPersonaje(String personaje, String descripcion) {
    }

    private record RespuestaPregunta(String codigo, String mensaje, boolean valida, String raw) {
    }

    private record VerificacionAdivinanza(boolean acierta, String mensaje, String raw) {
    }
}