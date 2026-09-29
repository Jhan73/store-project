package com.jhanantezana.jugueria.catalog.internal;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	Page<Product> findByCategoryId(UUID categoryId, Pageable pageable);

}
