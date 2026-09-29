package com.jhanantezana.jugueria.catalog.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModifierGroupRepository extends JpaRepository<ModifierGroup, UUID> {

	@EntityGraph(attributePaths = { "options", "options.allergens" })
	List<ModifierGroup> findAllByOrderByNameAsc();

	@EntityGraph(attributePaths = { "options", "options.allergens" })
	List<ModifierGroup> findAllByIdIn(Collection<UUID> ids);

	@EntityGraph(attributePaths = { "options", "options.allergens" })
	Optional<ModifierGroup> findWithOptionsById(UUID id);

}
