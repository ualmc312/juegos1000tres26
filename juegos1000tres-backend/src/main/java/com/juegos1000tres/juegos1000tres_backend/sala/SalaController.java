package com.juegos1000tres.juegos1000tres_backend.sala;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.juegos1000tres.juegos1000tres_backend.auth.AuthRole;
import com.juegos1000tres.juegos1000tres_backend.auth.AuthUser;
import com.juegos1000tres.juegos1000tres_backend.auth.JwtService;
import com.juegos1000tres.juegos1000tres_backend.modelos.Usuario;
import com.juegos1000tres.juegos1000tres_backend.repositorios.UsuarioRepository;

@RestController
@RequestMapping("/sala")
@CrossOrigin(origins = "*")
public class SalaController {

    private final SalaService salaService;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public SalaController(SalaService salaService, JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.salaService = salaService;
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/crear")
    public SalaRespuesta crearSala(@RequestParam(required = false) String nombre) {
        SalaActor actor = obtenerActor(nombre);
        return salaService.crearSala(actor.nombre(), actor.usuarioId(), actor.esInvitado());
    }

    @GetMapping("/{uuid}/unirse")
    public SalaRespuesta unirseSala(@PathVariable String uuid,
                                    @RequestParam(required = false) String nombre) {
        SalaActor actor = obtenerActor(nombre);
        return salaService.unirse(uuid, actor.nombre(), actor.usuarioId(), actor.esInvitado());
    }

    private SalaActor obtenerActor(String nombreFallback) {
        AuthUser user = jwtService.getCurrentUser();
        if (user == null) {
            return new SalaActor(nombreFallback, null, false);
        }

        boolean esInvitado = user.role() == AuthRole.GUEST;
        if (esInvitado) {
            return new SalaActor(user.nombre(), user.email(), true);
        }

        String usuarioId = usuarioRepository.findByEmailIgnoreCase(user.email())
                .map(Usuario::getId)
                .map(String::valueOf)
                .orElse(null);
        return new SalaActor(user.nombre(), usuarioId, false);
    }

    private record SalaActor(String nombre, String usuarioId, boolean esInvitado) {}

    @GetMapping("/{uuid}/estado")
    public SalaRespuesta estadoSala(@PathVariable String uuid) {
        return salaService.estado(uuid);
    }

    @PostMapping("/{uuid}/pantalla")
    public SalaRespuesta cambiarPantalla(@PathVariable String uuid,
                                         @RequestParam String actorId,
                                         @RequestParam String jugadorId) {
        return salaService.cambiarPantalla(uuid, actorId, jugadorId);
    }

    @PostMapping("/{uuid}/juego")
    public SalaRespuesta cambiarJuego(@PathVariable String uuid,
                                      @RequestParam String actorId,
                                      @RequestParam String juego) {
        return salaService.cambiarJuego(uuid, actorId, juego);
    }

    @PostMapping("/{uuid}/juego/finalizar")
    public ResponseEntity<Void> finalizarJuego(@PathVariable String uuid,
                                               @RequestParam String actorId) {
        salaService.finalizarJuego(uuid, actorId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{uuid}/victoria")
    public ResponseEntity<Void> sumarVictoria(@PathVariable String uuid,
                                              @RequestParam String jugadorId) {
        salaService.incrementarVictoria(uuid, jugadorId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{uuid}/puntuacion")
    public ResponseEntity<Void> establecerPuntuacion(@PathVariable String uuid,
                                                     @RequestParam String jugadorId,
                                                     @RequestParam int puntos) {
        salaService.establecerPuntuacion(uuid, jugadorId, puntos);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{uuid}/salir")
    public ResponseEntity<Void> salirSala(@PathVariable String uuid,
                                          @RequestParam String jugadorId) {
        salaService.salir(uuid, jugadorId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{uuid}/apagar")
    public ResponseEntity<Void> apagarSala(@PathVariable String uuid) {
        salaService.apagar(uuid);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(SalaNoEncontradaException.class)
    public ResponseEntity<ErrorRespuesta> manejarSalaNoEncontrada() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorRespuesta("uuid invalido"));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorRespuesta> manejarSinPermisos() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorRespuesta("permiso denegado"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorRespuesta> manejarDatosInvalidos() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorRespuesta("datos invalidos"));
    }
}
