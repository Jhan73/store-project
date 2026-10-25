package com.jhanantezana.jugueria.catalog.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	Page<Product> findByCategoryId(UUID categoryId, Pageable pageable);

	boolean existsByCategoryId(UUID categoryId);

	boolean existsByImageKey(String imageKey);

	// What a quick sale can ring up right now: pinned, on sale, in a category that is on the menu.
	@Query("select p from Product p where p.quickSalePinned = true and p.active = true and p.available = true "
			+ "and exists (select 1 from Category c where c.id = p.categoryId and c.active = true) "
			+ "order by p.displayOrder, lower(p.name), p.id")
	List<Product> findQuickSaleItems();

	// A conditional update: 0 rows means the flag already had that value (or the product does not exist).
	// It leaves the version alone on purpose, so an "86" never invalidates an admin's open edit.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "UPDATE catalog.product SET available = :available, updated_at = :now "
			+ "WHERE id = :id AND available <> :available", nativeQuery = true)
	int updateAvailability(@Param("id") UUID id, @Param("available") boolean available, @Param("now") Instant now);

	// Whoever stores this product's ID in a sales channel takes this lock first (pricing a line does), and the
	// delete takes the write lock below, so a line cannot slip in between the usage check and the delete. A key
	// share lock does not conflict with the updates of availability and of the product's own fields.
	@Query(value = "SELECT id FROM catalog.product WHERE id = :id FOR KEY SHARE", nativeQuery = true)
	Optional<UUID> lockAgainstDeletion(@Param("id") UUID id);

	// A native FOR UPDATE: Hibernate's pessimistic write lock is FOR NO KEY UPDATE on PostgreSQL, which does not
	// wait for the key share locks above.
	@Query(value = "SELECT id FROM catalog.product WHERE id = :id FOR UPDATE", nativeQuery = true)
	Optional<UUID> lockForDeletion(@Param("id") UUID id);

	@Query("select distinct p.categoryId from Product p where p.categoryId in :ids")
	Set<UUID> categoryIdsWithProducts(@Param("ids") Collection<UUID> categoryIds);

	@Query("select distinct g from Product p join p.modifierGroupIds g where g in :ids")
	Set<UUID> attachedModifierGroupIds(@Param("ids") Collection<UUID> groupIds);

}
