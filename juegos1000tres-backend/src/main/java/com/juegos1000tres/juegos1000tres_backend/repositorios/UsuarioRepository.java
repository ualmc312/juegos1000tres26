package com.juegos1000tres.juegos1000tres_backend.repositorios;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.juegos1000tres.juegos1000tres_backend.modelos.Usuario;
@CrossOrigin(origins = "http://localhost:4200")
@RepositoryRestResource(path = "usuarios", collectionResourceRel = "usuarios")
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @RestResource(path = "por-email", rel = "por-email")
    Optional<Usuario> findByEmail(String email);

    @RestResource(path = "login", rel = "login")
    Optional<Usuario> findByEmailAndPassword(String email, String password);

    @RestResource(path = "existe-email", rel = "existe-email")
    boolean existsByEmail(String email);

    @RestResource(path = "registro-disponible", rel = "registro-disponible")
    boolean existsByEmailOrNombre(String email, String nombre);

    /**
     * Busca un usuario por email (case-insensitive)
     */
    @RestResource(path = "por-email-insensitive", rel = "por-email-insensitive")
    Optional<Usuario> findByEmailIgnoreCase(String email);

    /**
     * Busca usuarios por email o nombre de forma parcial e insensible a mayúsculas.
     */
    List<Usuario> findTop10ByEmailContainingIgnoreCaseOrNombreContainingIgnoreCaseOrderByNombreAsc(String email, String nombre);

    /**
     * Verifica si existe email (case-insensitive)
     */
    @RestResource(path = "existe-email-insensitive", rel = "existe-email-insensitive")
    boolean existsByEmailIgnoreCase(String email);
}
