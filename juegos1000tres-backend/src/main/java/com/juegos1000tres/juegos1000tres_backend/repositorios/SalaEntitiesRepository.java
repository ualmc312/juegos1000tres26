package com.juegos1000tres.juegos1000tres_backend.repositorios;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.juegos1000tres.juegos1000tres_backend.modelos.SalaEntities;

@CrossOrigin(origins = "http://localhost:4200")
@RepositoryRestResource(path = "salas-entities", collectionResourceRel = "salas-entities")
public interface SalaEntitiesRepository extends JpaRepository<SalaEntities, Long> {

	@RestResource(path = "por-uuid", rel = "por-uuid")
	Optional<SalaEntities> findByUuid(String uuid);
}
