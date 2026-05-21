package com.juegos1000tres.juegos1000tres_backend.juegos.handicap;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.juegos1000tres.juegos1000tres_backend.sala.ErrorRespuesta;
import com.juegos1000tres.juegos1000tres_backend.sala.SalaNoEncontradaException;

@RestController
@RequestMapping("/sala/{uuid}/juego/handicap")
@CrossOrigin(origins = "*")
public class HandicapController {

    private final HandicapService handicapService;

    public HandicapController(HandicapService handicapService) {
        this.handicapService = handicapService;
    }

    @GetMapping("/estado")
    public HandicapEstadoRespuesta estado(@PathVariable String uuid) {
        return handicapService.obtenerEstado(uuid);
    }

    @PostMapping("/confirmar")
    public HandicapEstadoRespuesta confirmar(
            @PathVariable String uuid,
            @RequestParam String actorId,
            @RequestBody(required = false) HandicapSeleccionRequest payload) {
        List<String> ganadores = payload == null ? List.of() : payload.ganadores();
        return handicapService.confirmarSeleccion(uuid, actorId, ganadores);
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
