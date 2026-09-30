package com.jhanantezana.jugueria.store.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jhanantezana.jugueria.store.ReasonType;

public interface ReasonRepository extends JpaRepository<Reason, UUID> {

	List<Reason> findByType(ReasonType type);

}
