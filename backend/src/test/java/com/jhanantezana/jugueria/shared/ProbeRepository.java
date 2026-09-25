package com.jhanantezana.jugueria.shared;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProbeRepository extends JpaRepository<ProbeEntity, UUID> {
}
