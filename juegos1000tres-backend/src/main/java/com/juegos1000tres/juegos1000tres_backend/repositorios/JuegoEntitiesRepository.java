package com.juegos1000tres.juegos1000tres_backend.repositorios;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.juegos1000tres.juegos1000tres_backend.modelos.JuegoEntities;

@CrossOrigin(origins = "http://localhost:4200")
@RepositoryRestResource(path = "juegos-entities", collectionResourceRel = "juegos-entities")
public interface JuegoEntitiesRepository extends JpaRepository<JuegoEntities, Long> {

	@RestResource(path = "por-nombre", rel = "por-nombre")
	Optional<JuegoEntities> findByNombreIgnoreCase(String nombre);
}
