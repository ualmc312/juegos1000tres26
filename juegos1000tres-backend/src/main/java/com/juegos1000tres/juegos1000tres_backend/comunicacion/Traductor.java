package com.juegos1000tres.juegos1000tres_backend.comunicacion;

import java.util.Objects;

import com.juegos1000tres.juegos1000tres_backend.modelos.Juego;

public class Traductor<PAYLOAD> {

    private final Conexion<PAYLOAD> conexion;
    private final Envio<PAYLOAD> envio;
    private final Recibo<PAYLOAD> recibo;
    private final Class<PAYLOAD> clasePayload;

    public Traductor(Conexion<PAYLOAD> conexion, Envio<PAYLOAD> envio, Recibo<PAYLOAD> recibo) {
        this.conexion = Objects.requireNonNull(conexion, "La conexion es obligatoria");
        this.envio = Objects.requireNonNull(envio, "La estrategia de envio es obligatoria");
        this.recibo = Objects.requireNonNull(recibo, "La estrategia de recibo es obligatoria");
        this.clasePayload = this.conexion.getClasePayload();

        validarCompatibilidadDePayload(this.clasePayload, this.envio.getClasePayload(), "Envio");
        validarCompatibilidadDePayload(this.clasePayload, this.recibo.getClasePayload(), "Recibo");
    }

    public PAYLOAD traducirEnviableAFormato(Enviable enviable) {
        return envio.traducirEnviableAFormato(enviable);
    }

    public <T extends Enviable> T traducirFormatoAEnviable(PAYLOAD payload, Class<T> tipoEnviable) {
        return recibo.traducirFormatoAEnviable(payload, tipoEnviable);
    }

    public void enviar(Enviable enviable) {
        PAYLOAD payload = traducirEnviableAFormato(enviable);
        conexion.enviar(payload);
    }

    public <T extends Enviable> T recibir(Class<T> tipoEnviable) {
        PAYLOAD payload = conexion.recibir();
        return traducirFormatoAEnviable(payload, tipoEnviable);
    }

    public <T extends Enviable> T recibirYNotificarJuego(Juego juego, Class<T> tipoEnviable) {
        Objects.requireNonNull(juego, "El juego es obligatorio");
        Objects.requireNonNull(tipoEnviable, "El tipo de mensaje es obligatorio");

        T mensaje = recibir(tipoEnviable);
        juego.procesarMensajeEntrante(mensaje);
        return mensaje;
    }

    public Class<PAYLOAD> getClasePayload() {
        return clasePayload;
    }

    private void validarCompatibilidadDePayload(Class<PAYLOAD> claseBase, Class<PAYLOAD> claseDependencia, String dependencia) {
        if (!Objects.equals(claseBase, claseDependencia)) {
            throw new IllegalArgumentException(
                    "La clase de payload de " + dependencia + " no coincide con la de Conexion");
        }
    }
}