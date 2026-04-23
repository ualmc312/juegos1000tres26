package com.juegos1000tres.juegos1000tres_backend.comunicacion.implementaciones;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

public final class ComunicacionRuntimeConfig {

    static final String SYSTEM_PROPERTY_CONFIG_PATH = "juegos1000tres.comunicacion.config.path";
    static final String ENV_CONFIG_PATH = "JUEGOS1000TRES_COMUNICACION_CONFIG_PATH";

    private static final Path DEFAULT_EXTERNAL_CONFIG_PATH = Path.of("config", "comunicacion-runtime.properties");
    private static final String CLASSPATH_CONFIG_RESOURCE = "comunicacion-runtime.properties";

    private static final Properties PROPIEDADES = cargarPropiedades();

    private ComunicacionRuntimeConfig() {
    }

    public static String apiTipoComunicacion() {
        return obtenerTextoObligatorio("api.tipoComunicacion");
    }

    public static String apiHost() {
        return obtenerTextoObligatorio("api.host");
    }

    public static int apiPuerto() {
        return obtenerPuerto("api.puerto");
    }

    public static String apiEndpointSalaTemplate() {
        return obtenerTemplateConSala("api.endpoint.salaTemplate");
    }

    public static String apiEndpointActualizacionesSalaTemplate() {
        return obtenerTemplateConSala("api.endpoint.actualizaciones.salaTemplate");
    }

    public static String apiPayloadInicial() {
        return obtenerTextoObligatorio("api.payloadInicial");
    }

    public static String websocketTipoComunicacion() {
        return obtenerTextoObligatorio("ws.tipoComunicacion");
    }

    public static String websocketHost() {
        return obtenerTextoObligatorio("ws.host");
    }

    public static int websocketPuerto() {
        return obtenerPuerto("ws.puerto");
    }

    public static String websocketCanalSalaTemplate() {
        return obtenerTemplateConSala("ws.canal.salaTemplate");
    }

    public static String websocketPayloadVacio() {
        return obtenerTextoObligatorio("ws.payloadVacio");
    }

    private static Properties cargarPropiedades() {
        Properties propiedades = crearDefaults();

        String rutaConfigForzada = textoNoVacio(System.getProperty(SYSTEM_PROPERTY_CONFIG_PATH));
        if (rutaConfigForzada == null) {
            rutaConfigForzada = textoNoVacio(System.getenv(ENV_CONFIG_PATH));
        }

        if (rutaConfigForzada != null) {
            cargarDesdeRuta(Path.of(rutaConfigForzada), propiedades, true);
            return propiedades;
        }

        if (Files.exists(DEFAULT_EXTERNAL_CONFIG_PATH)) {
            cargarDesdeRuta(DEFAULT_EXTERNAL_CONFIG_PATH, propiedades, true);
            return propiedades;
        }

        cargarDesdeClasspath(propiedades);
        return propiedades;
    }

    private static Properties crearDefaults() {
        Properties defaults = new Properties();

        defaults.setProperty("api.tipoComunicacion", "API");
        defaults.setProperty("api.host", "127.0.0.1");
        defaults.setProperty("api.puerto", "8081");
        defaults.setProperty("api.endpoint.salaTemplate", "/api/salas/%s/eventos");
        defaults.setProperty("api.endpoint.actualizaciones.salaTemplate", "/api/salas/%s/actualizaciones");
        defaults.setProperty("api.payloadInicial", "{}");

        defaults.setProperty("ws.tipoComunicacion", "WEBSOCKET");
        defaults.setProperty("ws.host", "127.0.0.1");
        defaults.setProperty("ws.puerto", "8091");
        defaults.setProperty("ws.canal.salaTemplate", "/ws/salas/%s");
        defaults.setProperty("ws.payloadVacio", "{}");

        return defaults;
    }

    private static void cargarDesdeRuta(Path ruta, Properties target, boolean obligatoria) {
        Objects.requireNonNull(target, "Las propiedades de destino son obligatorias");
        Objects.requireNonNull(ruta, "La ruta de configuracion es obligatoria");

        try (InputStream input = Files.newInputStream(ruta)) {
            target.load(input);
        } catch (IOException ex) {
            if (obligatoria) {
                throw new IllegalStateException("No se pudo leer la configuracion externa en: " + ruta, ex);
            }
        }
    }

    private static void cargarDesdeClasspath(Properties target) {
        Objects.requireNonNull(target, "Las propiedades de destino son obligatorias");

        try (InputStream input = ComunicacionRuntimeConfig.class.getClassLoader()
                .getResourceAsStream(CLASSPATH_CONFIG_RESOURCE)) {
            if (input != null) {
                target.load(input);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer la configuracion de classpath: "
                    + CLASSPATH_CONFIG_RESOURCE, ex);
        }
    }

    private static String obtenerTextoObligatorio(String key) {
        String value = textoNoVacio(PROPIEDADES.getProperty(key));
        if (value == null) {
            throw new IllegalStateException("Falta la propiedad obligatoria: " + key);
        }

        return value;
    }

    private static int obtenerPuerto(String key) {
        String value = obtenerTextoObligatorio(key);

        try {
            int puerto = Integer.parseInt(value);
            if (puerto < 1 || puerto > 65535) {
                throw new IllegalStateException("El puerto configurado en " + key + " debe estar entre 1 y 65535");
            }
            return puerto;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("El puerto configurado en " + key + " no es numerico: " + value, ex);
        }
    }

    private static String obtenerTemplateConSala(String key) {
        String template = obtenerTextoObligatorio(key);
        if (!template.contains("%s")) {
            throw new IllegalStateException("La propiedad " + key + " debe incluir %s para el id de sala");
        }

        return template;
    }

    private static String textoNoVacio(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
