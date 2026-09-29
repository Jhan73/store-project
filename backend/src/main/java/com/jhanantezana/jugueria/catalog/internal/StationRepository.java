package com.jhanantezana.jugueria.catalog.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StationRepository extends JpaRepository<Station, UUID> {

	List<Station> findAllByOrderByNameAsc();

	Optional<Station> findByDefaultStationTrue();

}
