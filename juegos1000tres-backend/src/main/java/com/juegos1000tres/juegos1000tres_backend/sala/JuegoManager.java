package com.juegos1000tres.juegos1000tres_backend.sala;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Conexion;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Envio;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Recibo;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.juegos.Preguntas.PreguntasJuego;
import com.juegos1000tres.juegos1000tres_backend.juegos.SpaceInvaders.SpaceInvader;
import com.juegos1000tres.juegos1000tres_backend.juegos.taptap.TapTapJuego;
import com.juegos1000tres.juegos1000tres_backend.juegos.taptap.TapTapService;

/**
 * Gestiona instancias de juegos por sala. 
 * Cada instancia procesa mensajes síncronamente vía Spring controller,
 * sin usar ApiConexion embebida.
 */
@Service
public class JuegoManager {

    private final Map<String, GameInstance> instances = new ConcurrentHashMap<>();
    private final SalaService salaService;

    public JuegoManager(@Lazy SalaService salaService) {
        this.salaService = salaService;
    }

    public synchronized void crearInstanciaJuego(String salaUuid, String juego) {
        String key = keyFor(salaUuid, juego);
        if (instances.containsKey(key)) {
            return; // ya existe
        }

        if ("space-invaders".equalsIgnoreCase(juego)) {
            // Paso 1: Traductor placeholder para satisfacer el constructor de SpaceInvader.
            // El Recibo real con los eventos se construye después.
            Recibo<String> reciboBase = Recibo.paraJsonString();
            Traductor<String> placeholder = new Traductor<>(
                new InMemoryConexion(),
                Envio.paraStringDesdeOut(),
                reciboBase
            );

            // Paso 2: Crear el juego (los eventos internos referencian 'this').
            SpaceInvader juegoInstancia = new SpaceInvader(4, placeholder, placeholder, this.salaService, salaUuid);
            juegoInstancia.iniciar();

            // Paso 3: Registrar los eventos en un Recibo nuevo ahora que el juego existe.
            // Toda la comunicación dentro del juego pasa por el Traductor.
            Recibo<String> reciboConEventos = juegoInstancia.registrarEventosEnRecibo(Recibo.paraJsonString());

            // Paso 4: Construir el Traductor definitivo con los eventos correctamente registrados.
            Traductor<String> traductor = new Traductor<>(
                new InMemoryConexion(),
                Envio.paraStringDesdeOut(),
                reciboConEventos
            );

            GameInstance gi = new GameInstance(juegoInstancia, traductor);
            instances.put(key, gi);
        } else if ("preguntas".equalsIgnoreCase(juego)) {
            Recibo<String> reciboBase = Recibo.paraJsonString();
            Traductor<String> placeholder = new Traductor<>(
                new InMemoryConexion(),
                Envio.paraStringDesdeOut(),
                reciboBase
            );

            List<String> preguntasDefault = new ArrayList<>();
            preguntasDefault.add("Cual es el color favorito de [NOMBRE_JUGADOR]?");
            preguntasDefault.add("Que prefiere [NOMBRE_JUGADOR], playa o montana?");

            PreguntasJuego juegoInstancia = new PreguntasJuego(2, placeholder, placeholder, preguntasDefault, this.salaService, salaUuid);
            juegoInstancia.iniciar();

            Recibo<String> reciboConEventos = juegoInstancia.registrarEventosEnRecibo(Recibo.paraJsonString());

            Traductor<String> traductor = new Traductor<>(
                new InMemoryConexion(),
                Envio.paraStringDesdeOut(),
                reciboConEventos
            );

            GameInstance gi = new GameInstance(juegoInstancia, traductor);
            instances.put(key, gi);
        } else if ("taptap".equalsIgnoreCase(juego)) {
            Recibo<String> reciboBase = Recibo.paraJsonString();
            Traductor<String> placeholder = new Traductor<>(
                new InMemoryConexion(),
                Envio.paraStringDesdeOut(),
                reciboBase
            );

            TapTapService tapTapService = new TapTapService(this.salaService);
            TapTapJuego juegoInstancia = new TapTapJuego(tapTapService, salaUuid, placeholder, placeholder);
            juegoInstancia.iniciar();

            Recibo<String> reciboConEventos = juegoInstancia.registrarEventosEnRecibo(Recibo.paraJsonString());

            Traductor<String> traductor = new Traductor<>(
                new InMemoryConexion(),
                Envio.paraStringDesdeOut(),
                reciboConEventos
            );

            GameInstance gi = new GameInstance(juegoInstancia, traductor);
            instances.put(key, gi);
        }
    }

    public synchronized void detenerInstancia(String salaUuid, String juego) {
        String key = keyFor(salaUuid, juego);
        GameInstance gi = instances.remove(key);
        if (gi != null) {
            gi.juego.terminar();
        }
    }

    /**
     * Procesa un mensaje entrante (vía controller HTTP) y devuelve respuesta.
     */
    public String procesarMensaje(String salaUuid, String juego, String payload) {
        String key = keyFor(salaUuid, juego);
        GameInstance gi = instances.get(key);

        if (gi == null) {
            // Crear instancia si no existe (tolerancia)
            crearInstanciaJuego(salaUuid, juego);
            gi = instances.get(key);
            if (gi == null) {
                throw new IllegalStateException("No se pudo crear instancia de juego: " + juego);
            }
        }

        if (gi.juego instanceof PreguntasJuego preguntasJuego) {
            preguntasJuego.revisarTransicionesAutomaticas(System.currentTimeMillis());
        }

        var respuesta = gi.traductor.procesar(payload);
        return respuesta.orElse(null);
    }

    /**
     * Obtiene el estado actual del juego.
     */
    public String obtenerEstado(String salaUuid, String juego) {
        String key = keyFor(salaUuid, juego);
        GameInstance gi = instances.get(key);

        if (gi == null) {
            crearInstanciaJuego(salaUuid, juego);
            gi = instances.get(key);
            if (gi == null) {
                return "{}";
            }
        }

        if (gi.juego instanceof SpaceInvader spaceInvader) {
            return gi.traductor.traducirEnviableAFormato(spaceInvader.crearEstadoEnviable());
        }

        if (gi.juego instanceof PreguntasJuego preguntasJuego) {
            preguntasJuego.revisarTransicionesAutomaticas(System.currentTimeMillis());
            return gi.traductor.traducirEnviableAFormato(preguntasJuego.crearEstadoEnviable());
        }

        if (gi.juego instanceof TapTapJuego tapTapJuego) {
            return gi.traductor.traducirEnviableAFormato(tapTapJuego.crearEstadoEnviable());
        }

        return "{}";
    }

    private String keyFor(String sala, String juego) {
        return sala + ":" + (juego == null ? "" : juego.toLowerCase());
    }

    private static final class GameInstance {
        final com.juegos1000tres.juegos1000tres_backend.modelos.Juego juego;
        final Traductor<String> traductor;

        GameInstance(com.juegos1000tres.juegos1000tres_backend.modelos.Juego juego, Traductor<String> traductor) {
            this.juego = juego;
            this.traductor = traductor;
        }
    }

    /**
     * Conexión en memoria (no-op para uso interno del Traductor).
     */
    private static final class InMemoryConexion implements Conexion<String> {
        @Override
        public void conectar() { }

        @Override
        public void desconectar() { }

        @Override
        public void enviar(String payload) { }

        @Override
        public String recibir() { return null; }

        @Override
        public Class<String> getClasePayload() { return String.class; }

        @Override
        public String getTipoComunicacion() { return "INTERNAL"; }

        @Override
        public String getSalaId() { return "internal"; }

        @Override
        public String getCanalSala() { return "internal"; }
    }
}
