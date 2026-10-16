package com.jhanantezana.jugueria.store;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreApi {

	ZoneId timeZone();

	Currency currency();

	boolean isOpenAt(Instant instant);

	StoreThresholds thresholds();

	/** Whether an in-store sale closes even when the shelf cannot cover it, taking stock below zero. */
	boolean allowsNegativeStockOnSale();

	List<ReasonView> reasons(ReasonType type);

	Optional<DeliveryZoneView> deliveryZone(UUID zoneId);

}
