package com.jhanantezana.jugueria.catalog;

import java.util.UUID;

/**
 * Lets a module that keeps work routed to a station veto retiring it. Implemented as a bean by the module that owns
 * that work (the board), so {@code catalog} never depends on it.
 */
public interface StationDeactivationGuard {

	/** @throws com.jhanantezana.jugueria.shared.BusinessException when work is still routed to the station */
	void requireCanDeactivate(UUID stationId);

}
