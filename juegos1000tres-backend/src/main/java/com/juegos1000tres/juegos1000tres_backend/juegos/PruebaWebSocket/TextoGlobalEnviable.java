package com.juegos1000tres.juegos1000tres_backend.juegos.PruebaWebSocket;

import com.juegos1000tres.juegos1000tres_backend.comunicacion.Enviable;

public final class TextoGlobalEnviable extends Enviable {

    private String texto;

    public TextoGlobalEnviable() {
        this("");
    }

    public TextoGlobalEnviable(String texto) {
        this.texto = texto == null ? "" : texto;
    }

    @Override
    public Object out() {
        return "{\"comando\":\"" + PruebaWebSocket.COMANDO_TEXTO_GLOBAL
                + "\",\"texto\":\"" + escapeJson(this.texto) + "\"}";
    }

    @Override
    public void in(Object entrada) {
        if (entrada == null) {
            this.texto = "";
            return;
        }

        this.texto = entrada.toString();
    }

    public String getTexto() {
        return texto;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}