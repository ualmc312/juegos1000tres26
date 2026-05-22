package com.juegos1000tres.juegos1000tres_backend.repositorios;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.juegos1000tres.juegos1000tres_backend.modelos.EstadoSolicitudAmistad;
import com.juegos1000tres.juegos1000tres_backend.modelos.SolicitudAmistad;
import com.juegos1000tres.juegos1000tres_backend.modelos.Usuario;

@CrossOrigin(origins = "http://localhost:4200")
@RepositoryRestResource(path = "solicitudes-amistad", collectionResourceRel = "solicitudes-amistad")
public interface SolicitudAmistadRepository extends JpaRepository<SolicitudAmistad, Long> {

    /**
     * Obtiene todas las solicitudes pendientes recibidas por un usuario
     */
    List<SolicitudAmistad> findByUsuarioReceptorAndEstado(Usuario usuarioReceptor, EstadoSolicitudAmistad estado);

    /**
     * Obtiene todas las solicitudes pendientes enviadas por un usuario
     */
    List<SolicitudAmistad> findByUsuarioSolicitanteAndEstado(Usuario usuarioSolicitante, EstadoSolicitudAmistad estado);

    /**
     * Verifica si existe una solicitud entre dos usuarios
     */
    @Query("SELECT s FROM SolicitudAmistad s WHERE " +
           "(s.usuarioSolicitante = :usuario1 AND s.usuarioReceptor = :usuario2) OR " +
           "(s.usuarioSolicitante = :usuario2 AND s.usuarioReceptor = :usuario1)")
    Optional<SolicitudAmistad> findSolicitudBetween(Usuario usuario1, Usuario usuario2);

    /**
     * Obtiene todas las solicitudes de un usuario (tanto recibidas como enviadas)
     */
    @Query("SELECT s FROM SolicitudAmistad s WHERE " +
           "(s.usuarioReceptor = :usuario OR s.usuarioSolicitante = :usuario)")
    List<SolicitudAmistad> findAllByUsuario(Usuario usuario);
}
