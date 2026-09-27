package com.jhanantezana.jugueria.store.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreSettingsRepository extends JpaRepository<StoreSettings, UUID> {

	// Conditional update decides the race: 0 rows means someone else bumped it since expectedVersion was read.
	@Modifying(clearAutomatically = true)
	@Query("UPDATE StoreSettings s SET s.openingHoursVersion = s.openingHoursVersion + 1 "
			+ "WHERE s.id = :id AND s.openingHoursVersion = :expectedVersion")
	int bumpOpeningHoursVersion(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion);

}
