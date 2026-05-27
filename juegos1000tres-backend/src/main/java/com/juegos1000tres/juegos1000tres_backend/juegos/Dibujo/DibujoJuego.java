package com.juegos1000tres.juegos1000tres_backend.juegos.Dibujo;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.DestinoEnvio;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Recibo;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.ia.RespuestaIA;
import com.juegos1000tres.juegos1000tres_backend.ia.ServicioIA;
import com.juegos1000tres.juegos1000tres_backend.ia.SolicitudIA;
import com.juegos1000tres.juegos1000tres_backend.juegos.common.TemaSelector;
import com.juegos1000tres.juegos1000tres_backend.modelos.Juego;
import com.juegos1000tres.juegos1000tres_backend.sala.SalaService;

public class DibujoJuego extends Juego {

    public static final String COMANDO_REGISTRAR_JUGADOR = "REGISTRAR_JUGADOR";
    public static final String COMANDO_INICIAR_PARTIDA = "INICIAR_PARTIDA";
    public static final String COMANDO_PROPONER_TEMA = "PROPONER_TEMA";
    public static final String COMANDO_INTENTAR_ADIVINAR = "INTENTAR_ADIVINAR";
    public static final String COMANDO_ADD_STROKE = "ADD_STROKE";
    public static final String COMANDO_ERASE_LAST = "ERASE_LAST";
    public static final String COMANDO_CLEAR_DRAWING = "CLEAR_DRAWING";
    public static final String COMANDO_ESTADO_PARTIDA = "ESTADO_DIBUJO";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int TARGETS_POR_LOTE = 12;
    private static final Pattern ESPACIOS_MULTIPLES = Pattern.compile("\\s+");

    private final ServicioIA servicioIA;
    private final TemaSelector temaSelector;
    private final SalaService salaService;
    private final String salaId;

    private final Map<String, Jugador> jugadores = new LinkedHashMap<>();
    private final List<String> ordenRondas = new ArrayList<>();
    private final Random random = new Random();
    private final Map<String, ArrayDeque<String>> targetsPendientesPorTema = new LinkedHashMap<>();
    private final Map<String, Set<String>> targetsUsadosPorTema = new LinkedHashMap<>();
    private String ultimoTargetNormalizado = "";
    private final List<String> ganadores = new ArrayList<>();
    private final List<String> ganadoresNombres = new ArrayList<>();

    private FasePartida fase = FasePartida.ESPERANDO_TEMA;
    private boolean enCurso;
    private String tema = "";
    private String jugadorTemaId = null;

    private int indiceRondaActual = 0;
    private String jugadorDibujaId = null;

    private String target = "";
    private final List<Map<String, Object>> trazos = new ArrayList<>();
    private long inicioRondaEpochMs = 0L;
    private final long duracionRondaMs = 60_000L;
    private long proximaTransicionEpochMs = 0L;

    private final List<Map<String, Object>> resumenRondaActual = new ArrayList<>();
    private String mensajeEstado = "";

    public DibujoJuego(Traductor<?> conexionJugadores, Traductor<?> conexionPantalla, ServicioIA servicioIA, TemaSelector temaSelector, SalaService salaService, String salaId) {
        super(100, true, conexionJugadores, conexionPantalla);
        this.servicioIA = Objects.requireNonNull(servicioIA);
        this.temaSelector = Objects.requireNonNull(temaSelector);
        this.salaService = Objects.requireNonNull(salaService);
        this.salaId = Objects.requireNonNull(salaId);
    }

    public Recibo<String> registrarEventosEnRecibo(Recibo<String> reciboBase) {
        Objects.requireNonNull(reciboBase, "El recibo base es obligatorio");

        return reciboBase
                .conEvento(COMANDO_REGISTRAR_JUGADOR, new com.juegos1000tres.juegos1000tres_backend.juegos.Dibujo.RegistrarJugadorEvento(this))
                .conEvento(COMANDO_INICIAR_PARTIDA, new com.juegos1000tres.juegos1000tres_backend.juegos.Dibujo.IniciarPartidaEvento(this))
                .conEvento(COMANDO_PROPONER_TEMA, new com.juegos1000tres.juegos1000tres_backend.juegos.Dibujo.ProponerTemaEvento(this))
                .conEvento(COMANDO_INTENTAR_ADIVINAR, new com.juegos1000tres.juegos1000tres_backend.juegos.Dibujo.IntentarAdivinarEvento(this))
                .conEvento(COMANDO_ADD_STROKE, new com.juegos1000tres.juegos1000tres_backend.juegos.Dibujo.AddStrokeEvent(this))
                .conEvento(COMANDO_ERASE_LAST, new com.juegos1000tres.juegos1000tres_backend.juegos.Dibujo.EraseLastEvent(this))
                .conEvento(COMANDO_CLEAR_DRAWING, new com.juegos1000tres.juegos1000tres_backend.juegos.Dibujo.ClearDrawingEvent(this));
    }

    public synchronized void registrarJugadorDesdePayload(String payload) {
        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_REGISTRAR_JUGADOR);
        String jugadorId = leerTextoObligatorio(data, "jugadorId");
        String nombreJugador = leerTextoOpcional(data, "nombreJugador");

        this.jugadores.computeIfAbsent(jugadorId, id -> new Jugador(id, nombreJugador == null ? "Jugador" : nombreJugador));
        seleccionarJugadorTemaSiHaceFalta();
        publicarEstado();
    }

    public synchronized void iniciarPartidaDesdePayload(String payload) {
        if (this.jugadores.size() < 2) {
            this.fase = FasePartida.ESPERANDO_TEMA;
            this.mensajeEstado = "Necesitas al menos 2 jugadores";
            publicarEstado();
            return;
        }

        // seleccionar jugador aleatorio para proponer tema
        List<String> ids = new ArrayList<>(this.jugadores.keySet());
        Collections.shuffle(ids, this.random);
        this.jugadorTemaId = ids.get(0);
        this.fase = FasePartida.ESPERANDO_TEMA;
        this.enCurso = true;
        this.mensajeEstado = "Jugador elegido para proponer tema: " + this.jugadores.get(this.jugadorTemaId).nombre();
        publicarEstado();
    }

    public synchronized void proponerTemaDesdePayload(String payload) {
        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_PROPONER_TEMA);
        String jugadorId = leerTextoObligatorio(data, "jugadorId");
        String nombreJugador = leerTextoOpcional(data, "nombreJugador");
        String temaPropuesto = leerTextoObligatorio(data, "tema");

        this.jugadores.computeIfAbsent(jugadorId, id -> new Jugador(id, nombreJugador == null ? "Jugador" : nombreJugador));

        if (this.jugadorTemaId != null && !this.jugadorTemaId.equals(jugadorId)) {
            this.mensajeEstado = "No eres el jugador seleccionado para proponer tema";
            publicarEstado();
            return;
        }

        this.fase = FasePartida.VALIDANDO_TEMA;
        this.mensajeEstado = "Validando tema con IA...";
        publicarEstado();

        TemaSelector.ValidacionTema validacion = this.temaSelector.validarTema(temaPropuesto,
            "Analiza este tema para el juego 'Dibujo'. Para este juego acepta: 1) personajes concretos; 2) títulos de obras (series, películas, libros) que contengan personajes — en ese caso normaliza el tema al título de la obra; 3) categorías o conceptos generales adecuados para dibujar (por ejemplo: 'electrodomésticos', 'animales', 'herramientas'). Devuelve SOLO JSON con claves: valido(boolean), temaNormalizado(string), mensaje(string). Tema: %s",
            "Eres un validador de temas para un juego de dibujo. Acepta personajes, títulos de obras y categorías generales (ejemplos: electrodomésticos, animales). Responde solo JSON.", Map.of("juego", "dibujo"));

        if (!validacion.valido()) {
            this.fase = FasePartida.ESPERANDO_TEMA;
            // Mensaje amigable al frontend; conservar el detalle original en 'mensajeRaw' si se necesita para logs
            this.mensajeEstado = "Tema no válido. Prueba con un personaje conocido o con el título de una serie/película que tenga personajes.";
            publicarEstado();
            return;
        }

        this.tema = validacion.temaNormalizado();
        prepararBancoTargetsParaTema(this.tema);
        prepararRondas();
        iniciarRondaActual();
        publicarEstado();
    }

    public synchronized void intentarAdivinarDesdePayload(String payload) {
        if (this.fase != FasePartida.JUGANDO) {
            this.mensajeEstado = "No se puede adivinar ahora";
            publicarEstado();
            return;
        }

        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_INTENTAR_ADIVINAR);
        String jugadorId = leerTextoObligatorio(data, "jugadorId");
        String intento = leerTextoObligatorio(data, "intento");

        if (Objects.equals(jugadorId, this.jugadorDibujaId)) {
            this.mensajeEstado = "El dibujante no puede adivinar";
            publicarEstado();
            return;
        }

        // Evitar que adivine dos veces en la misma ronda
        boolean yaAcerto = this.resumenRondaActual.stream()
                .anyMatch(item -> Objects.equals(item.get("adivinador"), jugadorId) && Boolean.TRUE.equals(item.get("acierta")));
        if (yaAcerto) {
            return;
        }

        long ahora = System.currentTimeMillis();
        boolean acierta = verificarAdivinanzaLocal(this.target, intento);

        if (acierta) {
            long elapsed = ahora - this.inicioRondaEpochMs;
            int segundosTranscurridos = (int) (elapsed / 1000L);
            int bonus = (int) Math.max(0, (this.duracionRondaMs / 1000L) - segundosTranscurridos);

            // Pintor gana 15 por cada persona que acierta
            Jugador autor = this.jugadores.get(this.jugadorDibujaId);
            if (autor != null) {
                autor.puntos += 15;
            }

            // Adivinador gana el bonus
            Jugador adivinador = this.jugadores.get(jugadorId);
            if (adivinador != null) {
                adivinador.puntos += bonus;
                adivinador.mensajeEstado = "¡Correcto! Has adivinado la palabra";
                adivinador.resultadoIntento = "ACIERTO";
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("adivinador", jugadorId);
            item.put("intento", intento);
            item.put("acierta", true);
            item.put("tiempoMs", elapsed);
            this.resumenRondaActual.add(item);
            
            this.mensajeEstado = "Adivinado por " + (adivinador == null ? jugadorId : adivinador.nombre());

            // Si todos los adivinadores han acertado, terminar la ronda inmediatamente
            long adivinadoresActivos = this.jugadores.size() - 1;
            long aciertos = this.resumenRondaActual.stream()
                    .filter(i -> Boolean.TRUE.equals(i.get("acierta")))
                    .count();
            if (aciertos >= adivinadoresActivos && adivinadoresActivos > 0) {
                resolverRondaActual();
                this.proximaTransicionEpochMs = System.currentTimeMillis() + 5_000L;
            }

            publicarEstado();
            return;
        }

        Jugador adivinador = this.jugadores.get(jugadorId);
        if (adivinador != null) {
            adivinador.mensajeEstado = "No es correcta. Sigue intentándolo";
            adivinador.resultadoIntento = "FALLO";
        }
        publicarEstado();
    }

    public synchronized boolean revisarTransicionesAutomaticas(long ahoraMs) {
        if (this.fase == FasePartida.JUGANDO && this.proximaTransicionEpochMs > 0 && ahoraMs >= this.proximaTransicionEpochMs) {
            resolverRondaActual();

            // tras mostrar resultado entraremos en MOSTRANDO_RESULTADO con proximaTransicionEpochMs fijada
            this.proximaTransicionEpochMs = System.currentTimeMillis() + 5_000L;
            publicarEstado();
            return true;
        }

        if (this.fase == FasePartida.MOSTRANDO_RESULTADO && this.proximaTransicionEpochMs > 0 && ahoraMs >= this.proximaTransicionEpochMs) {
            if (this.indiceRondaActual + 1 < this.ordenRondas.size()) {
                this.indiceRondaActual += 1;
                iniciarRondaActual();
            } else {
                finalizarPartida();
            }

            this.proximaTransicionEpochMs = 0L;
            publicarEstado();
            return true;
        }

        return false;
    }

    public synchronized DibujoEstadoEnviable crearEstadoEnviable() {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("comando", COMANDO_ESTADO_PARTIDA);
        estado.put("fase", this.fase.name());
        estado.put("enCurso", this.enCurso);
        estado.put("tema", this.tema);
        estado.put("rondaActual", this.indiceRondaActual + 1);
        estado.put("totalRondas", this.ordenRondas.size());
        estado.put("mensaje", this.mensajeEstado);
        estado.put("jugadorTemaId", this.jugadorTemaId == null ? "" : this.jugadorTemaId);
        estado.put("jugadorDibujaId", this.jugadorDibujaId == null ? "" : this.jugadorDibujaId);
        estado.put("palabraSecreta", "");
        estado.put("resultadoIntento", "");
        estado.put("ganadores", new ArrayList<>(this.ganadores));
        estado.put("ganadoresNombres", new ArrayList<>(this.ganadoresNombres));
        estado.put("resumenRonda", new ArrayList<>(this.resumenRondaActual));
        estado.put("marcador", construirMarcador());
        estado.put("puedeAdivinar", this.fase == FasePartida.JUGANDO);

        long ahora = System.currentTimeMillis();
        long tiempoRestanteMs = 0L;
        if (this.proximaTransicionEpochMs > ahora) {
            tiempoRestanteMs = this.proximaTransicionEpochMs - ahora;
        }
        estado.put("tiempoRestanteMs", tiempoRestanteMs);

        return new DibujoEstadoEnviable(estado);
    }

    public synchronized DibujoEstadoEnviable crearEstadoPantallaEnviable() {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("comando", COMANDO_ESTADO_PARTIDA);
        estado.put("fase", this.fase.name());
        estado.put("enCurso", this.enCurso);
        estado.put("tema", this.tema);
        estado.put("rondaActual", this.indiceRondaActual + 1);
        estado.put("totalRondas", this.ordenRondas.size());
        estado.put("mensaje", this.mensajeEstado);
        estado.put("jugadorTemaId", this.jugadorTemaId == null ? "" : this.jugadorTemaId);
        estado.put("jugadorDibujaId", this.jugadorDibujaId == null ? "" : this.jugadorDibujaId);
        estado.put("palabraSecreta", (this.fase == FasePartida.MOSTRANDO_RESULTADO || this.fase == FasePartida.FINALIZADA) ? this.target : "");
        estado.put("resultadoIntento", "");
        estado.put("ganadores", new ArrayList<>(this.ganadores));
        estado.put("ganadoresNombres", new ArrayList<>(this.ganadoresNombres));

        long ahora = System.currentTimeMillis();
        List<Map<String, Object>> frame = new ArrayList<>(this.trazos);

        estado.put("drawingFrame", frame);
        estado.put("resumenRonda", new ArrayList<>(this.resumenRondaActual));
        estado.put("marcador", construirMarcador());
        estado.put("puedeAdivinar", this.fase == FasePartida.JUGANDO);

        long tiempoRestanteMs = 0L;
        if (this.proximaTransicionEpochMs > ahora) {
            tiempoRestanteMs = this.proximaTransicionEpochMs - ahora;
        }
        estado.put("tiempoRestanteMs", tiempoRestanteMs);

        return new DibujoEstadoEnviable(estado);
    }

    private void prepararRondas() {
        this.ordenRondas.clear();
        List<String> ids = new ArrayList<>(this.jugadores.keySet());
        Collections.shuffle(ids, this.random);
        // cada jugador pinta dos veces: concatenamos dos pasadas
        this.ordenRondas.addAll(ids);
        this.ordenRondas.addAll(ids);
        this.indiceRondaActual = 0;
    }

    private void iniciarRondaActual() {
        if (this.indiceRondaActual >= this.ordenRondas.size()) {
            finalizarPartida();
            return;
        }

        limpiarMensajesPrivados();
        this.resumenRondaActual.clear();
        this.jugadorDibujaId = this.ordenRondas.get(this.indiceRondaActual);

        // reset trazos (el pintor dibujará en tiempo real vía eventos)
        this.trazos.clear();

        // pedir a la IA una palabra objetivo del tema
        this.target = obtenerTargetParaTema(this.tema);
        if (this.target.isBlank()) {
            this.target = generarTargetDirectoConIA(this.tema);
        }
        String claveActual = normalizarComparacion(this.target);
        if (!claveActual.isBlank() && claveActual.equals(this.ultimoTargetNormalizado)) {
            String alternativo = obtenerTargetParaTema(this.tema);
            if (!alternativo.isBlank()) {
                this.target = alternativo;
                claveActual = normalizarComparacion(this.target);
            }
        }
        this.ultimoTargetNormalizado = claveActual;

        this.inicioRondaEpochMs = System.currentTimeMillis();
        this.proximaTransicionEpochMs = this.inicioRondaEpochMs + this.duracionRondaMs;
        this.fase = FasePartida.JUGANDO;
        
        Jugador pintor = this.jugadores.get(this.jugadorDibujaId);
        if (pintor != null) {
            pintor.mensajeEstado = "¡Te toca dibujar! Tu palabra secreta es: " + this.target;
        }
        this.mensajeEstado = "Dibuja: " + (pintor == null ? "Jugador" : pintor.nombre());
    }

    private void prepararBancoTargetsParaTema(String temaBase) {
        String claveTema = claveTemaCache(temaBase);
        if (claveTema.isBlank()) {
            return;
        }

        ArrayDeque<String> cola = this.targetsPendientesPorTema.computeIfAbsent(claveTema, _tema -> new ArrayDeque<>());
        Set<String> usados = this.targetsUsadosPorTema.computeIfAbsent(claveTema, _tema -> new LinkedHashSet<>());
        if (cola.size() >= 4) {
            return;
        }

        for (String candidato : solicitarTargetsEnLote(temaBase, TARGETS_POR_LOTE)) {
            agregarTargetSiEsNuevo(cola, usados, candidato);
        }
    }

    private String obtenerTargetParaTema(String temaBase) {
        String claveTema = claveTemaCache(temaBase);
        if (claveTema.isBlank()) {
            return "";
        }

        ArrayDeque<String> cola = this.targetsPendientesPorTema.computeIfAbsent(claveTema, _tema -> new ArrayDeque<>());
        Set<String> usados = this.targetsUsadosPorTema.computeIfAbsent(claveTema, _tema -> new LinkedHashSet<>());

        if (cola.isEmpty()) {
            prepararBancoTargetsParaTema(temaBase);
        }

        while (!cola.isEmpty()) {
            String candidato = limpiarCandidatoTarget(cola.pollFirst());
            String claveCandidato = normalizarComparacion(candidato);
            if (!candidato.isBlank() && !claveCandidato.isBlank() && !usados.contains(claveCandidato)) {
                usados.add(claveCandidato);
                return candidato;
            }
        }

        String directo = generarTargetDirectoConIA(temaBase);
        if (!directo.isBlank()) {
            usados.add(normalizarComparacion(directo));
        }
        return directo;
    }

    private List<String> solicitarTargetsEnLote(String temaBase, int cantidad) {
        String prompt = "Dame %d palabras o frases cortas, concretas y distintas sobre el tema '%s'. Devuelve SOLO un JSON array de strings, sin numeración, sin explicaciones y sin repetir elementos.".formatted(cantidad, temaBase);
        RespuestaIA resp = this.servicioIA.consultar(SolicitudIA.completa(prompt, "Generador de palabras para dibujo", null, 0.3d, 256, Map.of("juego", "dibujo", "tema", temaBase, "cantidad", cantidad)));

        List<String> candidatos = new ArrayList<>();
        String texto = limpiarRespuestaIA(resp.texto());
        if (texto.isBlank()) {
            return candidatos;
        }

        try {
            JsonNode json = OBJECT_MAPPER.readTree(texto);
            if (json.isArray()) {
                for (JsonNode item : json) {
                    if (item.isTextual()) {
                        candidatos.add(item.asText());
                    }
                }
            } else if (json.has("palabras") && json.get("palabras").isArray()) {
                for (JsonNode item : json.get("palabras")) {
                    if (item.isTextual()) {
                        candidatos.add(item.asText());
                    }
                }
            }
        } catch (IOException ex) {
            // fallback al parseo manual
        }

        if (candidatos.isEmpty()) {
            String[] partes = texto.split("[\\r\\n,;]+");
            for (String parte : partes) {
                String candidato = limpiarCandidatoTarget(parte);
                if (!candidato.isBlank()) {
                    candidatos.add(candidato);
                }
            }
        }

        return candidatos;
    }

    private String generarTargetDirectoConIA(String temaBase) {
        String prompt = "Dame UNA palabra concreta del siguiente tema: %s. Devuelve SOLO la palabra, sin explicaciones.".formatted(temaBase);
        RespuestaIA resp = this.servicioIA.consultar(SolicitudIA.completa(prompt, "Seleccionador de palabra para dibujo", null, 0.2d, 64, Map.of("juego", "dibujo")));
        String texto = resp.texto();
        if (texto == null) {
            return "";
        }

        String limpio = limpiarCandidatoTarget(texto);
        String[] partes = limpio.split("\\s+");
        return partes.length > 0 ? partes[0].replaceAll("[^\\p{L}0-9_-]", "").trim() : "";
    }

    private void agregarTargetSiEsNuevo(ArrayDeque<String> cola, Set<String> usados, String candidato) {
        String limpio = limpiarCandidatoTarget(candidato);
        String clave = normalizarComparacion(limpio);
        if (limpio.isBlank() || clave.isBlank() || usados.contains(clave) || colaContieneClave(cola, clave)) {
            return;
        }

        cola.addLast(limpio);
    }

    private boolean colaContieneClave(ArrayDeque<String> cola, String claveNormalizada) {
        for (String item : cola) {
            if (claveNormalizada.equals(normalizarComparacion(item))) {
                return true;
            }
        }
        return false;
    }

    private String limpiarRespuestaIA(String texto) {
        if (texto == null) {
            return "";
        }

        String limpio = texto.trim();
        if (limpio.startsWith("```")) {
            limpio = limpio.replaceFirst("^```(?:json)?\\s*", "");
            limpio = limpio.replaceFirst("\\s*```$", "");
        }

        int inicioArray = limpio.indexOf('[');
        int finArray = limpio.lastIndexOf(']');
        if (inicioArray >= 0 && finArray > inicioArray) {
            return limpio.substring(inicioArray, finArray + 1);
        }

        int inicioObjeto = limpio.indexOf('{');
        int finObjeto = limpio.lastIndexOf('}');
        if (inicioObjeto >= 0 && finObjeto > inicioObjeto) {
            return limpio.substring(inicioObjeto, finObjeto + 1);
        }

        return limpio;
    }

    private String limpiarCandidatoTarget(String candidato) {
        if (candidato == null) {
            return "";
        }

        String limpio = candidato.trim();
        if ((limpio.startsWith("\"") && limpio.endsWith("\"")) || (limpio.startsWith("'") && limpio.endsWith("'"))) {
            limpio = limpio.substring(1, limpio.length() - 1).trim();
        }

        return ESPACIOS_MULTIPLES.matcher(limpio).replaceAll(" ");
    }

    private String claveTemaCache(String temaBase) {
        return normalizarComparacion(temaBase);
    }

    private String normalizarComparacion(String texto) {
        if (texto == null) {
            return "";
        }

        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "")
                .trim();
    }

    private boolean verificarAdivinanzaLocal(String target, String intento) {
        String objetivoNormalizado = normalizarComparacion(target);
        String intentoNormalizado = normalizarComparacion(intento);

        if (objetivoNormalizado.isBlank() || intentoNormalizado.isBlank()) {
            return false;
        }

        if (objetivoNormalizado.equals(intentoNormalizado)) {
            return true;
        }

        return distanciaEdicionMaximaUno(objetivoNormalizado, intentoNormalizado);
    }

    private boolean distanciaEdicionMaximaUno(String primero, String segundo) {
        int longitudPrimero = primero.length();
        int longitudSegundo = segundo.length();
        if (Math.abs(longitudPrimero - longitudSegundo) > 1) {
            return false;
        }

        int indicePrimero = 0;
        int indiceSegundo = 0;
        int diferencias = 0;

        while (indicePrimero < longitudPrimero && indiceSegundo < longitudSegundo) {
            if (primero.charAt(indicePrimero) == segundo.charAt(indiceSegundo)) {
                indicePrimero++;
                indiceSegundo++;
                continue;
            }

            diferencias++;
            if (diferencias > 1) {
                return false;
            }

            if (longitudPrimero == longitudSegundo) {
                indicePrimero++;
                indiceSegundo++;
            } else if (longitudPrimero > longitudSegundo) {
                indicePrimero++;
            } else {
                indiceSegundo++;
            }
        }

        if (indicePrimero < longitudPrimero || indiceSegundo < longitudSegundo) {
            diferencias++;
        }

        return diferencias <= 1;
    }

    // eventos de dibujo
    public synchronized void addStrokeDesdePayload(String payload) {
        Map<String, Object> data = leerPayloadComoMapa(payload, COMANDO_ADD_STROKE);
        Map<String, Object> punto = new LinkedHashMap<>();
        if (data.containsKey("t")) punto.put("t", ((Number) data.get("t")).longValue());
        if (data.containsKey("x")) punto.put("x", ((Number) data.get("x")).intValue());
        if (data.containsKey("y")) punto.put("y", ((Number) data.get("y")).intValue());
        if (data.containsKey("down")) punto.put("down", Boolean.TRUE.equals(data.get("down")) || "true".equalsIgnoreCase(String.valueOf(data.get("down"))));
        this.trazos.add(punto);
        publicarEstado();
    }

    public synchronized void eraseLastDesdePayload(String payload) {
        // simplemente elimina el ultimo trazo si existe
        if (!this.trazos.isEmpty()) {
            this.trazos.remove(this.trazos.size() - 1);
            publicarEstado();
        }
    }

    public synchronized void clearDrawingDesdePayload(String payload) {
        this.trazos.clear();
        publicarEstado();
    }

    private void resolverRondaActual() {
        // mostrar scoreboard entre rondas y la palabra correcta
        limpiarMensajesPrivados();
        this.mensajeEstado = "Fin de ronda. La palabra era: " + this.target;
        this.fase = FasePartida.MOSTRANDO_RESULTADO;
    }

    private List<Map<String, Object>> construirMarcador() {
        List<Jugador> ordenados = this.jugadores.values().stream()
                .sorted(java.util.Comparator.comparingInt((Jugador j) -> j.puntos).reversed()
                        .thenComparing(Jugador::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Jugador j : ordenados) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("jugadorId", j.jugadorId);
            item.put("nombreJugador", j.nombre());
            item.put("puntos", j.puntos);
            resultado.add(item);
        }
        return resultado;
    }

    private void finalizarPartida() {
        if (this.fase == FasePartida.FINALIZADA) {
            return;
        }

        limpiarMensajesPrivados();
        this.fase = FasePartida.FINALIZADA;
        this.enCurso = false;
        this.proximaTransicionEpochMs = 0L;
        this.ganadores.clear();
        this.ganadoresNombres.clear();

        int mejorPuntuacion = this.jugadores.values().stream()
            .mapToInt(jugador -> jugador.puntos)
            .max()
            .orElse(0);

        List<Jugador> ganadoresPartida = this.jugadores.values().stream()
            .filter(jugador -> jugador.puntos == mejorPuntuacion)
            .sorted(java.util.Comparator.comparing(Jugador::nombre, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(jugador -> jugador.jugadorId, String.CASE_INSENSITIVE_ORDER))
            .toList();

        if (ganadoresPartida.isEmpty()) {
            this.mensajeEstado = "Partida finalizada";
            return;
        }

        for (Jugador ganador : ganadoresPartida) {
            String ganadorId = normalizarJugadorId(ganador.jugadorId, ganador.nombre());
            this.ganadores.add(ganadorId);
            this.ganadoresNombres.add(ganador.nombre());
            this.salaService.incrementarVictoria(this.salaId, ganadorId);
        }

        this.mensajeEstado = this.ganadores.size() == 1
            ? "Ganador: " + this.ganadoresNombres.get(0)
            : "Empate entre varios jugadores";

    }

    private String normalizarJugadorId(String jugadorId, String nombreJugador) {
        try {
            return this.salaService.normalizarJugadorId(this.salaId, jugadorId, nombreJugador);
        } catch (RuntimeException ex) {
            return jugadorId;
        }
    }

    public void publicarEstado() {
        DibujoEstadoEnviable estadoJugadores = crearEstadoEnviable();
        DibujoEstadoEnviable estadoPantalla = crearEstadoPantallaEnviable();

        Traductor<String> trad = traductorJugadores();
        try {
            String payloadBase = trad.traducirEnviableAFormato(estadoJugadores);
            com.fasterxml.jackson.databind.JsonNode root = OBJECT_MAPPER.readTree(payloadBase);

            for (String jugadorId : this.jugadores.keySet()) {
                com.fasterxml.jackson.databind.node.ObjectNode copia = OBJECT_MAPPER.createObjectNode();
                copia.setAll((com.fasterxml.jackson.databind.node.ObjectNode) root);

                // Sobrescribir campos privados
                Jugador jug = this.jugadores.get(jugadorId);
                if (jug != null) {
                    if (jug.mensajeEstado != null && !jug.mensajeEstado.isEmpty()) {
                        copia.put("mensaje", jug.mensajeEstado);
                    }
                    if (jug.resultadoIntento != null && !jug.resultadoIntento.isEmpty()) {
                        copia.put("resultadoIntento", jug.resultadoIntento);
                    }
                }

                if (jugadorId.equals(this.jugadorDibujaId)) {
                    copia.put("palabraSecreta", this.target);
                } else {
                    copia.put("palabraSecreta", "");
                }

                trad.enviar(
                        new DibujoEstadoEnviable(OBJECT_MAPPER.convertValue(copia, new TypeReference<Map<String, Object>>() {})),
                        DestinoEnvio.jugador(jugadorId));
            }
        } catch (IOException | RuntimeException ex) {
            trad.enviar(estadoJugadores);
        }

        traductorPantalla().enviar(estadoPantalla, DestinoEnvio.pantalla());
    }

    private void limpiarMensajesPrivados() {
        for (Jugador j : this.jugadores.values()) {
            j.mensajeEstado = "";
            j.resultadoIntento = "";
        }
    }

    @Override
    public void procesarMensajeEntrante(Enviable mensaje) {
        Objects.requireNonNull(mensaje, "El mensaje entrante es obligatorio");
        throw new UnsupportedOperationException("Dibujo procesa entradas mediante eventos + payload JSON");
    }

    private void seleccionarJugadorTemaSiHaceFalta() {
        if (!this.enCurso || this.jugadorTemaId != null || this.jugadores.size() < 2) {
            if (this.enCurso && this.jugadorTemaId == null && this.jugadores.size() < 2) {
                this.mensajeEstado = "Esperando al menos 2 jugadores";
            }
            return;
        }

        List<String> ids = new ArrayList<>(this.jugadores.keySet());
        Collections.shuffle(ids, this.random);
        this.jugadorTemaId = ids.get(0);
        this.fase = FasePartida.ESPERANDO_TEMA;
        this.mensajeEstado = "Jugador elegido para proponer tema: " + this.jugadores.get(this.jugadorTemaId).nombre();
    }

    @Override
    public void iniciar() {
        this.enCurso = true;
        seleccionarJugadorTemaSiHaceFalta();
        publicarEstado();
    }

    @Override
    public void terminar() {
        this.enCurso = false;
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
            @SuppressWarnings("unchecked")
            Map<String, Object> mapa = OBJECT_MAPPER.readValue(payload, Map.class);
            return mapa;
        } catch (IOException error) {
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
        MOSTRANDO_RESULTADO,
        FINALIZADA
    }

    private static final class Jugador {
        private final String jugadorId;
        private final String nombre;
        private int puntos = 0;
        private String mensajeEstado = "";
        private String resultadoIntento = "";

        private Jugador(String jugadorId, String nombre) {
            this.jugadorId = jugadorId;
            this.nombre = nombre;
        }

        public String nombre() { return this.nombre; }
    }

    public static final class DibujoEstadoEnviable extends com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable {
        private final Map<String, Object> estado;

        public DibujoEstadoEnviable(Map<String, Object> estado) {
            this.estado = estado;
        }

        @Override
        public Object out() {
            try {
                return OBJECT_MAPPER.writeValueAsString(this.estado);
            } catch (IOException e) {
                return "{}";
            }
        }

        @Override
        public void in(Object entrada) {
            // no aplica
        }
    }
}