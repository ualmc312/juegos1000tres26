package com.juegos1000tres.juegos1000tres_backend.juegos.Preguntas.pruebas;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Envio;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Recibo;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.implementaciones.ApiConexion;
import com.juegos1000tres.juegos1000tres_backend.juegos.Preguntas.PreguntasJuego;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class PreguntasPruebaService {

    private static final String SALA_ID_PREGUNTAS = "preguntas";
    private static final String PAYLOAD_VACIO = "{}";
    private static final long SLEEP_MS = 40L;

    private final ApiConexion conexionApi;
    private final PreguntasJuego juego;
    private final Traductor<String> traductorEventos;
    private final ExecutorService executorLoop;
    private final AtomicBoolean loopActivo;

    public PreguntasPruebaService() {
        this.conexionApi = new ApiConexion(SALA_ID_PREGUNTAS);

        Traductor<String> traductorApi = new Traductor<>(
                this.conexionApi,
                Envio.paraStringDesdeOut(),
                Recibo.paraJsonString());

        this.juego = new PreguntasJuego(
                12,
                traductorApi,
                traductorApi,
                cargarPreguntasDesdeResources());

        Recibo<String> reciboJuego = this.juego.registrarEventosEnRecibo(Recibo.paraJsonString());
        this.traductorEventos = new Traductor<>(
                this.conexionApi,
                Envio.paraStringDesdeOut(),
                reciboJuego);

        this.loopActivo = new AtomicBoolean(false);
        this.executorLoop = Executors.newSingleThreadExecutor((runnable) -> {
            Thread hilo = new Thread(runnable, "preguntas-api-loop");
            hilo.setDaemon(true);
            return hilo;
        });
    }

    @PostConstruct
    public void iniciar() {
        this.juego.iniciar();
        this.loopActivo.set(true);

        // Publica un primer estado para que cualquier cliente obtenga contexto inmediato.
        this.traductorEventos.enviar(this.juego.crearEstadoEnviable());

        this.executorLoop.submit(this::bucleProcesamiento);
    }

    @PreDestroy
    public void detener() {
        this.loopActivo.set(false);
        this.juego.terminar();
        this.executorLoop.shutdownNow();
        this.conexionApi.desconectar();
    }

    public String getUrlEventos() {
        return this.conexionApi.getUrlEndpointSala();
    }

    public String getUrlActualizaciones() {
        return this.conexionApi.getUrlEndpointActualizacionesSala();
    }

    private void bucleProcesamiento() {
        while (this.loopActivo.get()) {
            try {
                long ahoraMs = System.currentTimeMillis();
                if (this.juego.revisarTransicionesAutomaticas(ahoraMs)) {
                    this.traductorEventos.enviar(this.juego.crearEstadoEnviable(ahoraMs));
                }

                String payload = this.traductorEventos.recibirPayload();
                if (payload == null || payload.isBlank() || PAYLOAD_VACIO.equals(payload.trim())) {
                    dormirBreve();
                    continue;
                }

                Optional<String> respuesta = this.traductorEventos.procesar(payload);
                respuesta.ifPresent(this.traductorEventos::enviarPayload);
            } catch (RuntimeException _error) {
                dormirBreve();
            }
        }
    }

    private void dormirBreve() {
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private List<String> cargarPreguntasDesdeResources() {
        List<String> preguntas = new ArrayList<>();

        try (InputStream input = getClass().getClassLoader().getResourceAsStream("preguntas/preguntas.txt")) {
            if (input == null) {
                preguntas.add("Cual es la comida favorita de [NOMBRE_JUGADOR]?");
                return preguntas;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    String pregunta = linea.trim();
                    if (pregunta.isEmpty() || pregunta.startsWith("#")) {
                        continue;
                    }
                    preguntas.add(pregunta);
                }
            }
        } catch (Exception _error) {
            preguntas.clear();
            preguntas.add("Cual es la comida favorita de [NOMBRE_JUGADOR]?");
        }

        if (preguntas.isEmpty()) {
            preguntas.add("Cual es la comida favorita de [NOMBRE_JUGADOR]?");
        }

        return preguntas;
    }
}
