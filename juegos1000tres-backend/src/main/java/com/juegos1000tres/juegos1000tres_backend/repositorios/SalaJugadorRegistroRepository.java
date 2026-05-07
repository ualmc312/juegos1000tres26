package com.juegos1000tres.juegos1000tres_backend.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.juegos1000tres.juegos1000tres_backend.modelos.SalaJugadorRegistro;

@CrossOrigin(origins = "http://localhost:4200")
@RepositoryRestResource(path = "salas-jugadores", collectionResourceRel = "salas-jugadores")
public interface SalaJugadorRegistroRepository extends JpaRepository<SalaJugadorRegistro, Long> {

    @RestResource(path = "por-sala", rel = "por-sala")
    List<SalaJugadorRegistro> findBySalaId(Long salaId);

    @RestResource(path = "por-usuario", rel = "por-usuario")
    List<SalaJugadorRegistro> findByUsuarioId(Long usuarioId);
}
