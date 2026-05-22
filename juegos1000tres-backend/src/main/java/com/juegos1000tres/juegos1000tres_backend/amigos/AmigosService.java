package com.juegos1000tres.juegos1000tres_backend.amigos;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.juegos1000tres.juegos1000tres_backend.modelos.Amistad;
import com.juegos1000tres.juegos1000tres_backend.modelos.EstadoSolicitudAmistad;
import com.juegos1000tres.juegos1000tres_backend.modelos.SolicitudAmistad;
import com.juegos1000tres.juegos1000tres_backend.modelos.Usuario;
import com.juegos1000tres.juegos1000tres_backend.repositorios.AmistadRepository;
import com.juegos1000tres.juegos1000tres_backend.repositorios.SolicitudAmistadRepository;
import com.juegos1000tres.juegos1000tres_backend.repositorios.UsuarioRepository;

@Service
@Transactional
public class AmigosService {

    private final AmistadRepository amistadRepository;
    private final SolicitudAmistadRepository solicitudAmistadRepository;
    private final UsuarioRepository usuarioRepository;

    public AmigosService(AmistadRepository amistadRepository,
                        SolicitudAmistadRepository solicitudAmistadRepository,
                        UsuarioRepository usuarioRepository) {
        this.amistadRepository = amistadRepository;
        this.solicitudAmistadRepository = solicitudAmistadRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Envía una solicitud de amistad de un usuario a otro
     */
    public SolicitudAmistad enviarSolicitudAmistad(Long usuarioSolicitanteId, Long usuarioReceptorId) {
        // Validar que no sean el mismo usuario
        if (usuarioSolicitanteId.equals(usuarioReceptorId)) {
            throw new IllegalArgumentException("No puedes enviar una solicitud de amistad a ti mismo");
        }

        // Obtener usuarios
        Usuario usuarioSolicitante = usuarioRepository.findById(usuarioSolicitanteId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario solicitante no encontrado"));
        Usuario usuarioReceptor = usuarioRepository.findById(usuarioReceptorId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario receptor no encontrado"));

        // Verificar si ya son amigos
        if (amistadRepository.sonAmigos(usuarioSolicitante, usuarioReceptor)) {
            throw new IllegalArgumentException("Ya sois amigos");
        }

        // Verificar si ya existe una solicitud entre estos usuarios
        Optional<SolicitudAmistad> solicitudExistente = solicitudAmistadRepository.findSolicitudBetween(usuarioSolicitante, usuarioReceptor);
        if (solicitudExistente.isPresent()) {
            SolicitudAmistad solicitud = solicitudExistente.get();
            if (solicitud.getEstado() == EstadoSolicitudAmistad.PENDIENTE) {
                throw new IllegalArgumentException("Ya existe una solicitud pendiente entre estos usuarios");
            }
        }

        // Crear nueva solicitud
        SolicitudAmistad nuevaSolicitud = new SolicitudAmistad(usuarioSolicitante, usuarioReceptor);
        return solicitudAmistadRepository.save(nuevaSolicitud);
    }

    /**
     * Acepta una solicitud de amistad y crea la amistad
     */
    public Amistad aceptarSolicitud(Long solicitudId) {
        SolicitudAmistad solicitud = solicitudAmistadRepository.findById(solicitudId)
            .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));

        if (solicitud.getEstado() != EstadoSolicitudAmistad.PENDIENTE) {
            throw new IllegalArgumentException("La solicitud no está pendiente");
        }

        // Crear amistad
        Amistad amistad = new Amistad(solicitud.getUsuarioSolicitante(), solicitud.getUsuarioReceptor());
        amistad = amistadRepository.save(amistad);

        // Actualizar estado de la solicitud
        solicitud.setEstado(EstadoSolicitudAmistad.ACEPTADA);
        solicitudAmistadRepository.save(solicitud);

        return amistad;
    }

    /**
     * Rechaza una solicitud de amistad
     */
    public SolicitudAmistad rechazarSolicitud(Long solicitudId) {
        SolicitudAmistad solicitud = solicitudAmistadRepository.findById(solicitudId)
            .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada"));

        if (solicitud.getEstado() != EstadoSolicitudAmistad.PENDIENTE) {
            throw new IllegalArgumentException("La solicitud no está pendiente");
        }

        solicitud.setEstado(EstadoSolicitudAmistad.RECHAZADA);
        return solicitudAmistadRepository.save(solicitud);
    }

    /**
     * Elimina una amistad existente
     */
    public void eliminarAmistad(Long usuarioId1, Long usuarioId2) {
        Usuario usuario1 = usuarioRepository.findById(usuarioId1)
            .orElseThrow(() -> new IllegalArgumentException("Usuario 1 no encontrado"));
        Usuario usuario2 = usuarioRepository.findById(usuarioId2)
            .orElseThrow(() -> new IllegalArgumentException("Usuario 2 no encontrado"));

        Amistad amistad = amistadRepository.findAmistadBetween(usuario1, usuario2)
            .orElseThrow(() -> new IllegalArgumentException("No existe amistad entre estos usuarios"));

        amistadRepository.delete(amistad);
    }

    /**
     * Obtiene la lista de amigos de un usuario
     */
    public List<Usuario> obtenerAmigos(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        List<Amistad> amistades = amistadRepository.findAmigosDelUsuario(usuario);
        return amistades.stream()
            .map(amistad -> amistad.getOtroUsuario(usuario))
            .toList();
    }

    /**
     * Obtiene las solicitudes pendientes recibidas por un usuario
     */
    public List<SolicitudAmistad> obtenerSolicitudesRecibidas(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        return solicitudAmistadRepository.findByUsuarioReceptorAndEstado(usuario, EstadoSolicitudAmistad.PENDIENTE);
    }

    /**
     * Obtiene las solicitudes pendientes enviadas por un usuario
     */
    public List<SolicitudAmistad> obtenerSolicitudesEnviadas(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        return solicitudAmistadRepository.findByUsuarioSolicitanteAndEstado(usuario, EstadoSolicitudAmistad.PENDIENTE);
    }

    /**
     * Verifica si dos usuarios son amigos
     */
    public boolean sonAmigos(Long usuarioId1, Long usuarioId2) {
        Usuario usuario1 = usuarioRepository.findById(usuarioId1)
            .orElseThrow(() -> new IllegalArgumentException("Usuario 1 no encontrado"));
        Usuario usuario2 = usuarioRepository.findById(usuarioId2)
            .orElseThrow(() -> new IllegalArgumentException("Usuario 2 no encontrado"));

        return amistadRepository.sonAmigos(usuario1, usuario2);
    }

    /**
     * Busca un usuario por email
     */
    public Optional<Usuario> buscarUsuarioPorEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }
        return usuarioRepository.findByEmailIgnoreCase(email.trim());
    }

    /**
     * Busca usuarios por texto libre en email o nombre.
     */
    public List<Usuario> buscarUsuarios(String termino) {
        if (termino == null || termino.trim().isEmpty()) {
            return List.of();
        }

        String terminoLimpio = termino.trim();
        return usuarioRepository
            .findTop10ByEmailContainingIgnoreCaseOrNombreContainingIgnoreCaseOrderByNombreAsc(
                terminoLimpio,
                terminoLimpio);
    }
}
