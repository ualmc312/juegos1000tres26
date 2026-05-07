package com.juegos1000tres.juegos1000tres_backend.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.juegos1000tres.juegos1000tres_backend.modelos.SalaJuegoOrden;

@CrossOrigin(origins = "http://localhost:4200")
@RepositoryRestResource(path = "salas-juegos", collectionResourceRel = "salas-juegos")
public interface SalaJuegoOrdenRepository extends JpaRepository<SalaJuegoOrden, Long> {

    @RestResource(path = "por-sala", rel = "por-sala")
    List<SalaJuegoOrden> findBySalaIdOrderByOrdenAsc(Long salaId);
}
