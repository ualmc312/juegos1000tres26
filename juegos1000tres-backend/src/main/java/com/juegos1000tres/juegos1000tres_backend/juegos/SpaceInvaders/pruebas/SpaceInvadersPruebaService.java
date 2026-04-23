package com.juegos1000tres.juegos1000tres_backend.juegos.SpaceInvaders.pruebas;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Envio;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Recibo;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.implementaciones.ApiConexion;
import com.juegos1000tres.juegos1000tres_backend.juegos.SpaceInvaders.SpaceInvader;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class SpaceInvadersPruebaService {

    private static final String SALA_ID_SPACE_INVADERS = "space-invaders";
    private static final String PAYLOAD_VACIO = "{}";
    private static final long SLEEP_MS = 40L;

    private final ApiConexion conexionApi;
    private final SpaceInvader juego;
    private final Traductor<String> traductorEventos;
    private final ExecutorService executorLoop;
    private final AtomicBoolean loopActivo;

    public SpaceInvadersPruebaService() {
        this.conexionApi = new ApiConexion(SALA_ID_SPACE_INVADERS);

        Traductor<String> traductorApi = new Traductor<>(
                this.conexionApi,
                Envio.paraStringDesdeOut(),
                Recibo.paraJsonString());

        this.juego = new SpaceInvader(4, traductorApi, traductorApi);
        Recibo<String> reciboJuego = this.juego.registrarEventosEnRecibo(Recibo.paraJsonString());

        this.traductorEventos = new Traductor<>(
                this.conexionApi,
                Envio.paraStringDesdeOut(),
                reciboJuego);

        this.loopActivo = new AtomicBoolean(false);
        this.executorLoop = Executors.newSingleThreadExecutor((runnable) -> {
            Thread hilo = new Thread(runnable, "space-invaders-api-loop");
            hilo.setDaemon(true);
            return hilo;
        });
    }

    @PostConstruct
    public void iniciar() {
        this.juego.iniciar();
        this.loopActivo.set(true);

        // Publica un primer estado para que los clientes reciban contexto inicial.
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
}
