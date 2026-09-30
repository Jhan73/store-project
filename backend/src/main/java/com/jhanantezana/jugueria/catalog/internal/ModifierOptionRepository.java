package com.jhanantezana.jugueria.catalog.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModifierOptionRepository extends JpaRepository<ModifierOption, UUID> {

	// A conditional update: 0 rows means the flag already had that value (or the option does not exist).
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "UPDATE catalog.modifier_option SET available = :available "
			+ "WHERE id = :id AND available <> :available", nativeQuery = true)
	int updateAvailability(@Param("id") UUID id, @Param("available") boolean available);

}
