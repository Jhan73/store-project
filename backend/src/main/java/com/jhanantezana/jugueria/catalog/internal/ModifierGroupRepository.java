package com.jhanantezana.jugueria.catalog.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModifierGroupRepository extends JpaRepository<ModifierGroup, UUID> {

	@EntityGraph(attributePaths = "options")
	List<ModifierGroup> findAllByOrderByNameAsc();

	@EntityGraph(attributePaths = "options")
	List<ModifierGroup> findAllByIdIn(Collection<UUID> ids);

	@EntityGraph(attributePaths = "options")
	Optional<ModifierGroup> findWithOptionsById(UUID id);

	long countByIdIn(Collection<UUID> ids);

}
