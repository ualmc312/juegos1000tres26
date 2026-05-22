package com.juegos1000tres.juegos1000tres_backend.repositorios;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.juegos1000tres.juegos1000tres_backend.modelos.Amistad;
import com.juegos1000tres.juegos1000tres_backend.modelos.Usuario;
import org.springframework.data.repository.query.Param;

@CrossOrigin(origins = "http://localhost:4200")
@RepositoryRestResource(path = "amistades", collectionResourceRel = "amistades")
public interface AmistadRepository extends JpaRepository<Amistad, Long> {

    /**
     * Obtiene todos los amigos de un usuario
     */
    @Query("SELECT a FROM Amistad a WHERE " +
           "a.usuario1 = :usuario OR a.usuario2 = :usuario")
    List<Amistad> findAmigosDelUsuario(@Param("usuario") Usuario usuario);

    /**
     * Verifica si dos usuarios son amigos
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
           "FROM Amistad a WHERE " +
           "(a.usuario1 = :usuario1 AND a.usuario2 = :usuario2) OR " +
           "(a.usuario1 = :usuario2 AND a.usuario2 = :usuario1)")
    boolean sonAmigos(@Param("usuario1") Usuario usuario1, @Param("usuario2") Usuario usuario2);

    /**
     * Obtiene la amistad entre dos usuarios
     */
    @Query("SELECT a FROM Amistad a WHERE " +
           "(a.usuario1 = :usuario1 AND a.usuario2 = :usuario2) OR " +
           "(a.usuario1 = :usuario2 AND a.usuario2 = :usuario1)")
    Optional<Amistad> findAmistadBetween(@Param("usuario1") Usuario usuario1, @Param("usuario2") Usuario usuario2);
}
