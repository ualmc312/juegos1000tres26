package com.juegos1000tres.juegos1000tres_backend.repositorios;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import com.juegos1000tres.juegos1000tres_backend.modelos.Usuario;

import jakarta.transaction.Transactional;

@RepositoryRestResource(path = "usuarios", collectionResourceRel = "usuarios")
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @RestResource(path = "por-email", rel = "por-email")
    Optional<Usuario> findByEmail(String email);

    @RestResource(path = "login", rel = "login")
    Optional<Usuario> findByEmailAndPassword(String email, String password);

    @RestResource(path = "existe-email", rel = "existe-email")
    boolean existsByEmail(String email);

    @RestResource(path = "existe-nombre", rel = "existe-nombre")
    boolean existsByNombre(String nombre);

    @RestResource(path = "registro-disponible", rel = "registro-disponible")
    boolean existsByEmailOrNombre(String email, String nombre);

    @Transactional
    @Modifying
    @Query(
            value = "INSERT INTO usuarios (nombre, email, password) VALUES (:nombre, :email, :password)",
            nativeQuery = true)
    @RestResource(path = "registrar", rel = "registrar")
    int registrar(@Param("nombre") String nombre, @Param("email") String email, @Param("password") String password);
}
