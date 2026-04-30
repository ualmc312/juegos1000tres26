package com.juegos1000tres.juegos1000tres_backend.juegos.taptap;

import com.juegos1000tres.juegos1000tres_backend.sala.ErrorRespuesta;
import com.juegos1000tres.juegos1000tres_backend.sala.SalaNoEncontradaException;
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

@RestController
@RequestMapping("/sala/{uuid}/juego/taptap")
@CrossOrigin(origins = "*")
public class TapTapController {

    private final TapTapService tapTapService;

    public TapTapController(TapTapService tapTapService) {
        this.tapTapService = tapTapService;
    }

    @GetMapping("/estado")
    public TapTapEstadoRespuesta estado(@PathVariable String uuid) {
        return tapTapService.obtenerEstado(uuid);
    }

    @PostMapping("/punto")
    public TapTapPuntoRespuesta punto(@PathVariable String uuid,
                                      @RequestParam String jugadorId) {
        return tapTapService.registrarPunto(uuid, jugadorId);
    }

    @PostMapping("/finalizar")
    public TapTapFinalRespuesta finalizar(@PathVariable String uuid,
                                          @RequestParam String actorId) {
        return tapTapService.finalizar(uuid, actorId);
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
