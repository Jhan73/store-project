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

	List<ReasonView> reasons(ReasonType type);

	Optional<DeliveryZoneView> deliveryZone(UUID zoneId);

}
