package com.jhanantezana.jugueria.catalog;

import java.util.UUID;

/**
 * Lets another module veto staff marking a product available. Implemented as a bean by the module that owns the
 * reason (stock), so {@code catalog} never depends on it.
 */
public interface ProductAvailabilityGuard {

	/** @throws com.jhanantezana.jugueria.shared.BusinessException when the product cannot be offered right now */
	void requireCanBeMarkedAvailable(UUID productId);

}
