package com.jhanantezana.jugueria.catalog.internal;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	Page<Product> findByCategoryId(UUID categoryId, Pageable pageable);

	// A conditional update: 0 rows means the flag already had that value (or the product does not exist).
	// It leaves the version alone on purpose, so an "86" never invalidates an admin's open edit.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "UPDATE catalog.product SET available = :available, updated_at = :now "
			+ "WHERE id = :id AND available <> :available", nativeQuery = true)
	int updateAvailability(@Param("id") UUID id, @Param("available") boolean available, @Param("now") Instant now);

}
