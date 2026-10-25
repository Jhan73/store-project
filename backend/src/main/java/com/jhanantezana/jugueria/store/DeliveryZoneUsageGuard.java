package com.jhanantezana.jugueria.store;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Tells which delivery zones a module still refers to, by ID (orders that were placed for them). A zone any module
 * refers to can only be deactivated. Implemented as a bean by the module that stores zone IDs, so {@code store} never
 * reads its schema; the online ordering module adds its own implementation when it exists.
 */
public interface DeliveryZoneUsageGuard {

	/** The given zones this module refers to. Never throws and takes no lock. */
	Set<UUID> zonesInUse(Collection<UUID> zoneIds);

}
