package com.juegos1000tres.juegos1000tres_backend.amigos;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.juegos1000tres.juegos1000tres_backend.modelos.Amistad;
import com.juegos1000tres.juegos1000tres_backend.modelos.SolicitudAmistad;
import com.juegos1000tres.juegos1000tres_backend.modelos.Usuario;

@RestController
@RequestMapping("/api/amigos")
@CrossOrigin(origins = "http://localhost:4200")
public class AmigosController {

    private final AmigosService amigosService;
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public AmigosController(AmigosService amigosService) {
        this.amigosService = amigosService;
    }

    /**
     * Envía una solicitud de amistad
     * POST /api/amigos/solicitar
     */
    @PostMapping("/solicitar")
    public ResponseEntity<?> enviarSolicitud(@RequestBody Map<String, Long> request) {
        try {
            Long usuarioSolicitanteId = request.get("usuarioSolicitanteId");
            Long usuarioReceptorId = request.get("usuarioReceptorId");

            if (usuarioSolicitanteId == null || usuarioReceptorId == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "usuarioSolicitanteId y usuarioReceptorId son requeridos"));
            }

            SolicitudAmistad solicitud = amigosService.enviarSolicitudAmistad(usuarioSolicitanteId, usuarioReceptorId);
            SolicitudAmistadRespuesta respuesta = convertirSolicitudAmistad(solicitud);

            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al enviar solicitud: " + e.getMessage()));
        }
    }

    /**
     * Acepta una solicitud de amistad
     * PUT /api/amigos/aceptar/{solicitudId}
     */
    @PutMapping("/aceptar/{solicitudId}")
    public ResponseEntity<?> aceptarSolicitud(@PathVariable Long solicitudId) {
        try {
            Amistad amistad = amigosService.aceptarSolicitud(solicitudId);
            AmistadRespuesta respuesta = convertirAmistad(amistad);

            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al aceptar solicitud: " + e.getMessage()));
        }
    }

    /**
     * Rechaza una solicitud de amistad
     * PUT /api/amigos/rechazar/{solicitudId}
     */
    @PutMapping("/rechazar/{solicitudId}")
    public ResponseEntity<?> rechazarSolicitud(@PathVariable Long solicitudId) {
        try {
            SolicitudAmistad solicitud = amigosService.rechazarSolicitud(solicitudId);
            SolicitudAmistadRespuesta respuesta = convertirSolicitudAmistad(solicitud);

            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al rechazar solicitud: " + e.getMessage()));
        }
    }

    /**
     * Elimina una amistad existente
     * DELETE /api/amigos/{usuarioId1}/{usuarioId2}
     */
    @DeleteMapping("/{usuarioId1}/{usuarioId2}")
    public ResponseEntity<?> eliminarAmistad(@PathVariable Long usuarioId1, @PathVariable Long usuarioId2) {
        try {
            amigosService.eliminarAmistad(usuarioId1, usuarioId2);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al eliminar amistad: " + e.getMessage()));
        }
    }

    /**
     * Obtiene la lista de amigos de un usuario
     * GET /api/amigos/mis-amigos/{usuarioId}
     */
    @GetMapping("/mis-amigos/{usuarioId}")
    public ResponseEntity<?> obtenerAmigos(@PathVariable Long usuarioId) {
        try {
            List<Usuario> amigos = amigosService.obtenerAmigos(usuarioId);
            List<UsuarioRespuesta> respuesta = amigos.stream()
                .map(this::convertirUsuario)
                .toList();

            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al obtener amigos: " + e.getMessage()));
        }
    }

    /**
     * Obtiene las solicitudes pendientes recibidas por un usuario
     * GET /api/amigos/solicitudes-recibidas/{usuarioId}
     */
    @GetMapping("/solicitudes-recibidas/{usuarioId}")
    public ResponseEntity<?> obtenerSolicitudesRecibidas(@PathVariable Long usuarioId) {
        try {
            List<SolicitudAmistad> solicitudes = amigosService.obtenerSolicitudesRecibidas(usuarioId);
            List<SolicitudAmistadRespuesta> respuesta = solicitudes.stream()
                .map(this::convertirSolicitudAmistad)
                .toList();

            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al obtener solicitudes recibidas: " + e.getMessage()));
        }
    }

    /**
     * Obtiene las solicitudes pendientes enviadas por un usuario
     * GET /api/amigos/solicitudes-enviadas/{usuarioId}
     */
    @GetMapping("/solicitudes-enviadas/{usuarioId}")
    public ResponseEntity<?> obtenerSolicitudesEnviadas(@PathVariable Long usuarioId) {
        try {
            List<SolicitudAmistad> solicitudes = amigosService.obtenerSolicitudesEnviadas(usuarioId);
            List<SolicitudAmistadRespuesta> respuesta = solicitudes.stream()
                .map(this::convertirSolicitudAmistad)
                .toList();

            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al obtener solicitudes enviadas: " + e.getMessage()));
        }
    }

    /**
     * Verifica si dos usuarios son amigos
     * GET /api/amigos/son-amigos/{usuarioId1}/{usuarioId2}
     */
    @GetMapping("/son-amigos/{usuarioId1}/{usuarioId2}")
    public ResponseEntity<?> sonAmigos(@PathVariable Long usuarioId1, @PathVariable Long usuarioId2) {
        try {
            boolean sonAmigos = amigosService.sonAmigos(usuarioId1, usuarioId2);
            return ResponseEntity.ok(Map.of("sonAmigos", sonAmigos));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al verificar amistad: " + e.getMessage()));
        }
    }

    /**
     * Busca un usuario por email
     * GET /api/amigos/buscar?email=...
     */
    @GetMapping("/buscar")
    public ResponseEntity<?> buscarPorEmail(@RequestParam String email) {
        try {
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email es requerido"));
            }

            String terminoLimpio = email.trim();
            var usuarios = amigosService.buscarUsuarios(terminoLimpio);
            if (usuarios.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Usuario no encontrado: " + terminoLimpio));
            }

            List<UsuarioRespuesta> respuesta = usuarios.stream()
                .map(this::convertirUsuario)
                .toList();
            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error al buscar usuario: " + e.getMessage()));
        }
    }

    // Métodos auxiliares para convertir entidades a DTOs

    private SolicitudAmistadRespuesta convertirSolicitudAmistad(SolicitudAmistad solicitud) {
        return new SolicitudAmistadRespuesta(
            solicitud.getId(),
            convertirUsuario(solicitud.getUsuarioSolicitante()),
            convertirUsuario(solicitud.getUsuarioReceptor()),
            solicitud.getEstado().toString(),
            solicitud.getFechaCreacion().format(formatter)
        );
    }

    private AmistadRespuesta convertirAmistad(Amistad amistad) {
        return new AmistadRespuesta(
            amistad.getId(),
            convertirUsuario(amistad.getUsuario1()),
            convertirUsuario(amistad.getUsuario2()),
            amistad.getFechaCreacion().format(formatter)
        );
    }

    private UsuarioRespuesta convertirUsuario(Usuario usuario) {
        return new UsuarioRespuesta(
            usuario.getId(),
            usuario.getNombre(),
            usuario.getEmail()
        );
    }
}
