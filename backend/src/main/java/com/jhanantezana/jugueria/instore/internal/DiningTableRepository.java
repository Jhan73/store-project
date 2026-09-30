package com.jhanantezana.jugueria.instore.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DiningTableRepository extends JpaRepository<DiningTable, UUID> {

	List<DiningTable> findAllByOrderByDisplayOrderAscNameAsc();

	List<DiningTable> findAllByActiveTrueOrderByDisplayOrderAscNameAsc();

}
