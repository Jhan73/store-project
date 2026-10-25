package com.jhanantezana.jugueria.catalog;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Tells which products a module still refers to, by ID, in what it keeps (ticket lines, board cards, sales rows,
 * stock history). A product any module refers to can only be deactivated, so its history keeps resolving. Implemented
 * as a bean by every module that stores product IDs, so {@code catalog} never reads their schemas; a module that
 * starts storing product IDs (online orders) adds its own implementation.
 */
public interface ProductUsageGuard {

	/** The given products this module refers to. Never throws and takes no lock. */
	Set<UUID> productsInUse(Collection<UUID> productIds);

}
