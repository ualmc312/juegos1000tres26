package com.juegos1000tres.juegos1000tres_backend.juegos.AdivinaElPersonaje;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Envio;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Recibo;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.implementaciones.ComunicacionRuntimeConfig;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.implementaciones.WebSocketConexion;
import com.juegos1000tres.juegos1000tres_backend.ia.ServicioIA;
import com.juegos1000tres.juegos1000tres_backend.juegos.common.TemaSelector;
import com.juegos1000tres.juegos1000tres_backend.sala.SalaService;

@Service
public class AdivinaElPersonajeManager {

    private static final long SLEEP_MS = 40L;
    private static final String PAYLOAD_VACIO = "{}";

    private final Map<String, InstanciaSala> instancias = new ConcurrentHashMap<>();
    private final ServicioIA servicioIA;
    private final TemaSelector temaSelector;
    private final SalaService salaService;

    public AdivinaElPersonajeManager(ServicioIA servicioIA, TemaSelector temaSelector, @Lazy SalaService salaService) {
        this.servicioIA = servicioIA;
        this.temaSelector = temaSelector;
        this.salaService = salaService;
    }

    public synchronized void crearInstanciaParaSala(String salaUuid) {
        if (instancias.containsKey(salaUuid)) {
            return;
        }

        int puerto = ComunicacionRuntimeConfig.websocketPuerto();
        WebSocketConexion conexionJugadores = new WebSocketConexion(salaUuid, "jugadores", puerto);
        WebSocketConexion conexionPantalla = new WebSocketConexion(salaUuid, "pantalla", puerto);

        Traductor<String> traductorJugadores = new Traductor<>(
                conexionJugadores,
                Envio.paraStringDesdeOut(),
                Recibo.paraJsonString());

        Traductor<String> traductorPantalla = new Traductor<>(
                conexionPantalla,
                Envio.paraStringDesdeOut(),
                Recibo.paraJsonString());

        AdivinaElPersonajeJuego juego = new AdivinaElPersonajeJuego(traductorJugadores, traductorPantalla, this.servicioIA, this.temaSelector, this.salaService, salaUuid);
        Recibo<String> reciboEventos = juego.registrarEventosEnRecibo(Recibo.paraJsonString());
        Traductor<String> traductorEventos = new Traductor<>(
                conexionJugadores,
                Envio.paraStringDesdeOut(),
                reciboEventos);

        juego.iniciar();

        AtomicBoolean activo = new AtomicBoolean(true);
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread hilo = new Thread(runnable, "adivina-personaje-loop-" + salaUuid);
            hilo.setDaemon(true);
            return hilo;
        });

        executor.submit(() -> bucleProcesamiento(traductorEventos, activo));

        instancias.put(salaUuid, new InstanciaSala(
                juego,
                traductorEventos,
                conexionJugadores,
                conexionPantalla,
                activo,
                executor));
    }

    public synchronized void detenerInstanciaParaSala(String salaUuid) {
        InstanciaSala instancia = instancias.remove(salaUuid);
        if (instancia == null) {
            return;
        }

        instancia.activo().set(false);
        instancia.juego().terminar();
        instancia.executor().shutdownNow();
        instancia.conexionJugadores().desconectar();
        instancia.conexionPantalla().desconectar();
    }

    private void bucleProcesamiento(Traductor<String> traductorEventos, AtomicBoolean activo) {
        while (activo.get()) {
            try {
                String payload = traductorEventos.recibirPayload();
                if (payload == null || payload.isBlank() || PAYLOAD_VACIO.equals(payload.trim())) {
                    dormirBreve();
                    continue;
                }

                traductorEventos.procesar(payload);
            } catch (RuntimeException ex) {
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

    private record InstanciaSala(
            AdivinaElPersonajeJuego juego,
            Traductor<String> traductorEventos,
            WebSocketConexion conexionJugadores,
            WebSocketConexion conexionPantalla,
            AtomicBoolean activo,
            ExecutorService executor) {
    }
}