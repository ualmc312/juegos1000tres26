package com.juegos1000tres.juegos1000tres_backend.sala;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.juegos1000tres.juegos1000tres_backend.auth.AuthRole;
import com.juegos1000tres.juegos1000tres_backend.auth.AuthUser;
import com.juegos1000tres.juegos1000tres_backend.auth.JwtService;
import com.juegos1000tres.juegos1000tres_backend.modelos.SalaEntities;
import com.juegos1000tres.juegos1000tres_backend.modelos.SalaJuegoOrden;
import com.juegos1000tres.juegos1000tres_backend.modelos.SalaJuegoResultado;
import com.juegos1000tres.juegos1000tres_backend.repositorios.SalaEntitiesRepository;
import com.juegos1000tres.juegos1000tres_backend.repositorios.UsuarioRepository;

@RestController
@RequestMapping("/api/historial")
public class HistorialController {

    private final SalaEntitiesRepository salaEntitiesRepository;
        private final UsuarioRepository usuarioRepository;
        private final JwtService jwtService;

        public HistorialController(
                        SalaEntitiesRepository salaEntitiesRepository,
                        UsuarioRepository usuarioRepository,
                        JwtService jwtService) {
        this.salaEntitiesRepository = salaEntitiesRepository;
                this.usuarioRepository = usuarioRepository;
                this.jwtService = jwtService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<HistorialSalaRespuesta> obtenerHistorial() {
        Long usuarioId = obtenerUsuarioActualId();
        String nombreUsuario = obtenerUsuarioActualNombre();
        if (usuarioId == null) {
            return List.of();
        }

        return salaEntitiesRepository.findAll(Sort.by(Sort.Direction.DESC, "creadaEn"))
                .stream()
                .map(sala -> mapearSala(sala, usuarioId, nombreUsuario))
                .filter(sala -> sala != null)
                .toList();
    }

        private Long obtenerUsuarioActualId() {
                AuthUser current = jwtService.getCurrentUser();
                if (current == null || current.role() != AuthRole.USER) {
                        return null;
                }

                return usuarioRepository.findByEmailIgnoreCase(current.email())
                                .map(usuario -> usuario.getId())
                                .orElse(null);
        }

        private String obtenerUsuarioActualNombre() {
                AuthUser current = jwtService.getCurrentUser();
                if (current == null || current.role() != AuthRole.USER) {
                        return null;
                }

                return current.nombre();
        }

    private HistorialSalaRespuesta mapearSala(SalaEntities sala, Long usuarioId, String nombreUsuario) {
        boolean esHost = usuarioId.equals(sala.getHostUsuarioId())
                || (sala.getHostUsuarioId() == null
                && nombreUsuario != null
                && nombreUsuario.equalsIgnoreCase(sala.getHostNombre()));

        List<HistorialJuegoRespuesta> juegos = sala.getJuegosJugados().stream()
                .map(juego -> mapearJuego(juego, usuarioId, nombreUsuario, esHost))
                .filter(juego -> juego != null)
                .toList();

        if (juegos.isEmpty()) {
            return null;
        }

        return new HistorialSalaRespuesta(
                sala.getUuid(),
                sala.getHostNombre(),
                sala.getCreadaEn() != null ? sala.getCreadaEn().toString() : "",
                juegos);
    }

    private HistorialJuegoRespuesta mapearJuego(SalaJuegoOrden juego, Long usuarioId, String nombreUsuario, boolean esHost) {
        List<SalaJuegoResultado> resultados = juego.getJugadores();

        boolean participo = esHost || resultados.stream()
                .anyMatch(resultado -> perteneceAlUsuario(resultado, usuarioId, nombreUsuario));

        if (!participo) {
            return null;
        }

        List<HistorialResultadoRespuesta> jugadores = resultados.stream()
                .map(this::mapearResultado)
                .toList();

        List<String> ganadores = jugadores.stream()
                .filter(HistorialResultadoRespuesta::victoria)
                .map(HistorialResultadoRespuesta::nombreJugador)
                .toList();

        int puntuacionTotal = juego.getJugadores().stream()
                .mapToInt(SalaJuegoResultado::getPuntuacion)
                .sum();

        return new HistorialJuegoRespuesta(
                juego.getOrden(),
                juego.getFechaJugado() != null ? juego.getFechaJugado().toString() : "",
                juego.getJuego() != null ? juego.getJuego().getNombre() : "Juego",
                juego.getJuego() != null ? juego.getJuego().getRuta() : "",
                puntuacionTotal,
                jugadores,
                ganadores);
    }

    private HistorialResultadoRespuesta mapearResultado(SalaJuegoResultado resultado) {
        return new HistorialResultadoRespuesta(
                resultado.getUsuarioId(),
                resultado.getNombreJugador(),
                resultado.getPuntuacion(),
                resultado.getPosicion(),
                resultado.isVictoria());
    }

    private boolean perteneceAlUsuario(SalaJuegoResultado resultado, Long usuarioId, String nombreUsuario) {
        if (resultado.getUsuarioId() != null) {
            return usuarioId.equals(resultado.getUsuarioId());
        }

        return nombreUsuario != null
                && resultado.getNombreJugador() != null
                && nombreUsuario.equalsIgnoreCase(resultado.getNombreJugador());
    }

    public record HistorialSalaRespuesta(
            String uuid,
            String hostNombre,
            String creadaEn,
            List<HistorialJuegoRespuesta> juegosJugados) {
    }

    public record HistorialJuegoRespuesta(
            int orden,
            String fechaJugado,
            String juegoNombre,
            String juegoRuta,
            int puntuacionTotal,
            List<HistorialResultadoRespuesta> jugadores,
            List<String> ganadores) {
    }

    public record HistorialResultadoRespuesta(
            Long usuarioId,
            String nombreJugador,
            int puntuacion,
            Integer posicion,
            boolean victoria) {
    }
}