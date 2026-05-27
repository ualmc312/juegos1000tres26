package com.juegos1000tres.juegos1000tres_backend.sala;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.juegos1000tres.juegos1000tres_backend.auth.AuthRole;
import com.juegos1000tres.juegos1000tres_backend.auth.AuthUser;
import com.juegos1000tres.juegos1000tres_backend.auth.JwtService;
import com.juegos1000tres.juegos1000tres_backend.modelos.JuegoEntities;
import com.juegos1000tres.juegos1000tres_backend.modelos.SalaEntities;
import com.juegos1000tres.juegos1000tres_backend.modelos.SalaJuegoOrden;
import com.juegos1000tres.juegos1000tres_backend.modelos.SalaJuegoResultado;
import com.juegos1000tres.juegos1000tres_backend.modelos.Usuario;
import com.juegos1000tres.juegos1000tres_backend.repositorios.SalaEntitiesRepository;
import com.juegos1000tres.juegos1000tres_backend.repositorios.UsuarioRepository;

class HistorialControllerTest {

    @Test
    void obtenerHistorialIncluyeTodosLosJugadoresCuandoElUsuarioParticipo() {
        SalaEntities sala = new SalaEntities("483143", "b", LocalDate.of(2026, 5, 27));
        sala.setHostUsuarioId(1L);

        JuegoEntities juegoEntidad = new JuegoEntities(2, "reflejos-p2p", "descripcion", "reflejos-p2p");
        SalaJuegoOrden juego = new SalaJuegoOrden(sala, juegoEntidad, 1);

        SalaJuegoResultado host = new SalaJuegoResultado(juego, "b", 1L, 1, true);
        host.setPosicion(1);
        SalaJuegoResultado invitado = new SalaJuegoResultado(juego, "a", 7L, 0, false);
        invitado.setPosicion(2);

        juego.registrarJugador(host);
        juego.registrarJugador(invitado);
        sala.registrarJuego(juego);

        SalaEntitiesRepository salaRepository = proxy(SalaEntitiesRepository.class, (proxy, method, args) -> {
            if ("findAll".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof Sort) {
                return List.of(sala);
            }

            return valorPorDefecto(method.getReturnType());
        });

        Usuario usuario = new Usuario("a", "a@example.com", "secret");
        asignarId(usuario, 7L);

        UsuarioRepository usuarioRepository = proxy(UsuarioRepository.class, (proxy, method, args) -> {
            if ("findByEmailIgnoreCase".equals(method.getName())) {
                return Optional.of(usuario);
            }

            return valorPorDefecto(method.getReturnType());
        });

        JwtService jwtService = new JwtService("01234567890123456789012345678901", 60) {
            @Override
            public AuthUser getCurrentUser() {
                return new AuthUser("a", "a@example.com", AuthRole.USER);
            }
        };

        HistorialController controller = new HistorialController(salaRepository, usuarioRepository, jwtService);

        List<HistorialController.HistorialSalaRespuesta> historial = controller.obtenerHistorial();

        assertEquals(1, historial.size());
        HistorialController.HistorialSalaRespuesta salaRespuesta = historial.get(0);
        assertEquals("483143", salaRespuesta.uuid());
        assertEquals(1, salaRespuesta.juegosJugados().size());

        HistorialController.HistorialJuegoRespuesta juegoRespuesta = salaRespuesta.juegosJugados().get(0);
        assertEquals(2, juegoRespuesta.jugadores().size());
        assertEquals(List.of("b"), juegoRespuesta.ganadores());
        assertFalse(juegoRespuesta.jugadores().stream().allMatch(jugador -> "a".equals(jugador.nombreJugador())));
        assertTrue(juegoRespuesta.jugadores().stream().anyMatch(jugador -> "b".equals(jugador.nombreJugador())));
        assertTrue(juegoRespuesta.jugadores().stream().anyMatch(jugador -> "a".equals(jugador.nombreJugador())));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    private static Object valorPorDefecto(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }

        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == char.class) {
            return '\0';
        }

        return null;
    }

    private static void asignarId(Usuario usuario, Long id) {
        try {
            Field field = Usuario.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(usuario, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}