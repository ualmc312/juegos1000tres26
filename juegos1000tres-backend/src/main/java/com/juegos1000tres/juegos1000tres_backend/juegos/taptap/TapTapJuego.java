package com.juegos1000tres.juegos1000tres_backend.juegos.taptap;

import java.util.Objects;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Recibo;
import com.juegos1000tres.juegos1000tres_backend.comunicacion.Traductor;
import com.juegos1000tres.juegos1000tres_backend.modelos.Juego;

/**
 * Adaptador que integra TapTapService en la arquitectura de Juego.
 * Expone eventos REGISTRAR_PUNTO y FINALIZAR para comunicarse vía Traductor.
 */
public class TapTapJuego extends Juego {

    private final TapTapService tapTapService;
    private final String salaId;

    public TapTapJuego(TapTapService tapTapService, String salaId, Traductor<?> conexionJugadores,
            Traductor<?> conexionPantalla) {
        super(2, false, conexionJugadores, conexionPantalla);
        this.tapTapService = Objects.requireNonNull(tapTapService, "TapTapService es obligatorio");
        this.salaId = Objects.requireNonNull(salaId, "El ID de sala es obligatorio");
    }

    /**
     * Registra los eventos de TapTap en el Recibo.
     */
    public Recibo<String> registrarEventosEnRecibo(Recibo<String> reciboBase) {
        return reciboBase
            .conEvento("REGISTRAR_PUNTO", new TapTapRegistrarPuntoEvento(this.tapTapService, this.salaId))
            .conEvento("FINALIZAR", new TapTapFinalizarEvento(this.tapTapService, this.salaId));
    }

    /**
     * Crea el estado actual del juego como Enviable.
     */
    public TapTapEstadoEnviable crearEstadoEnviable() {
        try {
            TapTapEstadoRespuesta estado = this.tapTapService.obtenerEstado(this.salaId);
            return new TapTapEstadoEnviable(estado.toMap());
        } catch (Exception e) {
            return new TapTapEstadoEnviable();
        }
    }

    @Override
    public void procesarMensajeEntrante(Enviable mensaje) {
        // Los mensajes se procesan a través del Traductor vía los eventos registrados.
        // Este método es un hook por si necesita procesamiento adicional.
    }

    @Override
    public void iniciar() {
        // TapTap no requiere inicialización especial.
    }

    @Override
    public void terminar() {
        // TapTap no requiere limpieza especial.
    }
}
