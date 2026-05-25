package com.juegos1000tres.juegos1000tres_backend.repositorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.juegos1000tres.juegos1000tres_backend.modelos.SalaJuegoResultado;

@CrossOrigin(origins = "http://localhost:4200")
@RepositoryRestResource(path = "salas-juegos-resultados", collectionResourceRel = "salas-juegos-resultados")
public interface SalaJuegoResultadoRepository extends JpaRepository<SalaJuegoResultado, Long> {

    @RestResource(path = "por-sala-juego", rel = "por-sala-juego")
    List<SalaJuegoResultado> findBySalaJuegoId(Long salaJuegoId);

    @RestResource(path = "por-jugador", rel = "por-jugador")
    List<SalaJuegoResultado> findByNombreJugadorIgnoreCase(String nombreJugador);
}
