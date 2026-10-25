package com.jhanantezana.jugueria.catalog.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface StationRepository extends JpaRepository<Station, UUID> {

	List<Station> findAllByOrderByNameAsc();

	List<Station> findAllByActiveTrueOrderByNameAsc();

	Optional<Station> findByDefaultStationTrue();

	// Held until the caller commits: a category being placed on the station and the station being retired cannot
	// both pass their checks, because the write lock waits for the share locks of the categories in flight.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Station s where s.id = :id")
	Optional<Station> findForUpdateById(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select s from Station s where s.id = :id")
	Optional<Station> findForShareById(@Param("id") UUID id);

}
