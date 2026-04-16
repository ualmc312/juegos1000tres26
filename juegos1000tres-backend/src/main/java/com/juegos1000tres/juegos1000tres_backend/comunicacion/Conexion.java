package com.juegos1000tres.juegos1000tres_backend.comunicacion;

public interface Conexion<PAYLOAD> {

    String TIPO_COMPROBAR_HOMOLOGO = "ComprobarHomologo";

    void conectar();

    void desconectar();

    void enviar(PAYLOAD payload);

    PAYLOAD recibir();

    Class<PAYLOAD> getClasePayload();

    String getTipoComunicacion();

    default String getTipoVerificacion() {
        return TIPO_COMPROBAR_HOMOLOGO;
    }

    default boolean comprobarHomologo(String tipoComunicacionFront) {
        if (tipoComunicacionFront == null || tipoComunicacionFront.isBlank()) {
            return false;
        }

        return getTipoComunicacion().equalsIgnoreCase(tipoComunicacionFront.trim());
    }
}
