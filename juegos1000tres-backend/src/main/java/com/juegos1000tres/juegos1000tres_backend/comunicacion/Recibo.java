package com.juegos1000tres.juegos1000tres_backend.comunicacion;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Recibo<PAYLOAD> {

    private static final Pattern PATRON_COMANDO_JSON = Pattern.compile("\\\"comando\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final Class<PAYLOAD> clasePayload;
    private final Function<PAYLOAD, String> extractorComando;
    private final BiConsumer<PAYLOAD, ContextoEvento> procesadorPersonalizado;
    private final Map<String, Evento<PAYLOAD>> eventosPorComando;

    public Recibo(Class<PAYLOAD> clasePayload, Function<PAYLOAD, String> extractorComando) {
        this(clasePayload, extractorComando, null, Map.of());
    }

    public Recibo(Class<PAYLOAD> clasePayload, BiConsumer<PAYLOAD, ContextoEvento> procesadorPersonalizado) {
        this(clasePayload, null, procesadorPersonalizado, Map.of());
    }

    private Recibo(
            Class<PAYLOAD> clasePayload,
            Function<PAYLOAD, String> extractorComando,
            BiConsumer<PAYLOAD, ContextoEvento> procesadorPersonalizado,
            Map<String, Evento<PAYLOAD>> eventosPorComando) {
        this.clasePayload = Objects.requireNonNull(clasePayload, "La clase de payload es obligatoria");
        this.extractorComando = extractorComando;
        this.procesadorPersonalizado = procesadorPersonalizado;
        this.eventosPorComando = Map.copyOf(eventosPorComando);
    }

    public Recibo<PAYLOAD> conEvento(String comando, Evento<PAYLOAD> evento) {
        if (this.extractorComando == null) {
            throw new IllegalStateException("Este Recibo no soporta enrutado por comando");
        }

        String comandoNormalizado = normalizarComando(comando);
        Evento<PAYLOAD> eventoNoNulo = Objects.requireNonNull(evento, "El evento es obligatorio");

        Map<String, Evento<PAYLOAD>> nuevoMapa = new LinkedHashMap<>(this.eventosPorComando);
        nuevoMapa.put(comandoNormalizado, eventoNoNulo);

        return new Recibo<>(
                this.clasePayload,
                this.extractorComando,
                this.procesadorPersonalizado,
                nuevoMapa);
    }

    public void procesar(PAYLOAD payload, ContextoEvento contexto) {
        Objects.requireNonNull(contexto, "El contexto de evento es obligatorio");

        if (this.procesadorPersonalizado != null) {
            this.procesadorPersonalizado.accept(payload, contexto);
            return;
        }

        if (payload == null) {
            throw new IllegalArgumentException("El payload entrante no puede estar vacio");
        }

        if (this.extractorComando == null) {
            throw new IllegalStateException("No existe extractor de comando para este Recibo");
        }

        String comando = this.extractorComando.apply(payload);
        Evento<PAYLOAD> evento = this.eventosPorComando.get(normalizarComando(comando));
        if (evento == null) {
            throw new IllegalArgumentException("No existe un evento registrado para el comando: " + comando);
        }

        evento.hacer(payload, contexto);
    }

    public Class<PAYLOAD> getClasePayload() {
        return this.clasePayload;
    }

    public static Recibo<String> paraJsonString() {
        return new Recibo<>(String.class, Recibo::extraerComandoDesdeJsonString);
    }

    private static String extraerComandoDesdeJsonString(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("El payload entrante no puede estar vacio");
        }

        Matcher matcher = PATRON_COMANDO_JSON.matcher(payload);
        if (!matcher.find()) {
            throw new IllegalArgumentException("El payload no incluye el campo comando");
        }

        return matcher.group(1);
    }

    private String normalizarComando(String comando) {
        if (comando == null || comando.isBlank()) {
            throw new IllegalArgumentException("El comando del evento es obligatorio");
        }

        return comando.trim().toLowerCase(Locale.ROOT);
    }
}