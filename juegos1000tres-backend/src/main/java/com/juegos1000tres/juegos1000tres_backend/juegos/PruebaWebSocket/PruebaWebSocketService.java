package com.juegos1000tres.juegos1000tres_backend.juegos.PruebaWebSocket;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Envio;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Recibo;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.implementaciones.WebSocketConexion;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class PruebaWebSocketService {

    private static final String PAYLOAD_VACIO = "{}";
    private static final long SLEEP_MS = 40L;

    private static final String SALA_ID_JUGADORES = "prueba-websocket-jugadores";
    private static final String SALA_ID_PANTALLA = "prueba-websocket-pantalla";

    private final WebSocketConexion conexionJugadores;
    private final WebSocketConexion conexionPantalla;
    private final PruebaWebSocket juego;
    private final Traductor<String> traductorEventosJugadores;
    private final ExecutorService executorLoop;
    private final AtomicBoolean loopActivo;

    public PruebaWebSocketService() {
        this.conexionJugadores = new WebSocketConexion(SALA_ID_JUGADORES);
        this.conexionPantalla = new WebSocketConexion(SALA_ID_PANTALLA);

        Envio<String> envio = Envio.paraStringDesdeOut();
        Recibo<String> reciboVacio = Recibo.paraJsonString();

        Traductor<String> traductorCanalJugadores = new Traductor<>(
                this.conexionJugadores,
                envio,
                reciboVacio);

        Traductor<String> traductorCanalPantalla = new Traductor<>(
                this.conexionPantalla,
            Envio.paraStringDesdeOut(),
            Recibo.paraJsonString());

        this.juego = new PruebaWebSocket(traductorCanalJugadores, traductorCanalPantalla);

        Recibo<String> reciboEventos = Recibo.paraJsonString()
                .conEvento(PruebaWebSocket.COMANDO_ENVIAR_TEXTO, new EnviarTextoEvento(this.juego));

        this.traductorEventosJugadores = new Traductor<>(
                this.conexionJugadores,
                Envio.paraStringDesdeOut(),
                reciboEventos);

        this.loopActivo = new AtomicBoolean(false);
        this.executorLoop = Executors.newSingleThreadExecutor((runnable) -> {
            Thread hilo = new Thread(runnable, "prueba-websocket-loop");
            hilo.setDaemon(true);
            return hilo;
        });
    }

    @PostConstruct
    public void iniciar() {
        this.juego.iniciar();
        this.loopActivo.set(true);
        this.executorLoop.submit(this::bucleProcesamiento);
    }

    @PreDestroy
    public void detener() {
        this.loopActivo.set(false);
        this.juego.terminar();
        this.executorLoop.shutdownNow();

        this.conexionJugadores.desconectar();
        this.conexionPantalla.desconectar();
    }

    private void bucleProcesamiento() {
        while (this.loopActivo.get()) {
            try {
                String payload = this.traductorEventosJugadores.recibirPayload();
                if (payload == null || payload.isBlank() || PAYLOAD_VACIO.equals(payload.trim())) {
                    dormirBreve();
                    continue;
                }

                this.traductorEventosJugadores.procesar(payload);
            } catch (RuntimeException error) {
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
}