package com.juegos1000tres.juegos1000tres_backend.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.juegos1000tres.juegos1000tres_backend.modelos.SalaRegistro;

@CrossOrigin(origins = "http://localhost:4200")
@RepositoryRestResource(path = "salas", collectionResourceRel = "salas")
public interface SalaRegistroRepository extends JpaRepository<SalaRegistro, Long> {

    @RestResource(path = "por-host", rel = "por-host")
    List<SalaRegistro> findByHostUsuarioId(Long hostUsuarioId);

}
