package com.jhanantezana.jugueria.store.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Singletons;
import com.jhanantezana.jugueria.store.DeliveryZoneCreated;
import com.jhanantezana.jugueria.store.DeliveryZoneStatusChanged;
import com.jhanantezana.jugueria.store.DeliveryZoneUpdated;
import com.jhanantezana.jugueria.store.StoreError;

@Service
public class DeliveryZoneService {

	private final DeliveryZoneRepository zones;

	private final StoreSettingsRepository settings;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	DeliveryZoneService(DeliveryZoneRepository zones, StoreSettingsRepository settings,
			ApplicationEventPublisher events, CurrentActor currentActor, Clock clock) {
		this.zones = zones;
		this.settings = settings;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<DeliveryZone> list() {
		return zones.findAll();
	}

	@Transactional
	public DeliveryZone create(String name, Money fee, int deliveryMinutes, @Nullable Money minimumOrder,
			@Nullable Money freeDeliveryThreshold) {
		requireStoreCurrency(fee, minimumOrder, freeDeliveryThreshold);
		var now = Instant.now(clock);
		var zone = new DeliveryZone(name, fee, deliveryMinutes, minimumOrder, freeDeliveryThreshold, now);
		try {
			zones.saveAndFlush(zone);
		}
		catch (DataIntegrityViolationException e) {
			throw nameAlreadyUsed();
		}
		events.publishEvent(new DeliveryZoneCreated(zone.getId(), name, fee, deliveryMinutes, currentActor.id(),
				currentActor.role(), now));
		return zone;
	}

	@Transactional
	public DeliveryZone change(UUID id, String name, Money fee, int deliveryMinutes, @Nullable Money minimumOrder,
			@Nullable Money freeDeliveryThreshold, long expectedVersion) {
		var zone = findOrThrow(id);
		requireMatchingVersion(zone, expectedVersion);
		requireStoreCurrency(fee, minimumOrder, freeDeliveryThreshold);
		var before = zone.snapshot();
		var now = Instant.now(clock);
		zone.change(name, fee, deliveryMinutes, minimumOrder, freeDeliveryThreshold, now);
		try {
			zones.flush();
		}
		catch (DataIntegrityViolationException e) {
			throw nameAlreadyUsed();
		}
		var after = zone.snapshot();
		events.publishEvent(
				new DeliveryZoneUpdated(zone.getId(), before, after, currentActor.id(), currentActor.role(), now));
		return zone;
	}

	@Transactional
	public DeliveryZone deactivate(UUID id, long expectedVersion) {
		var zone = findOrThrow(id);
		requireMatchingVersion(zone, expectedVersion);
		if (!zone.isActive()) {
			return zone;
		}
		var now = Instant.now(clock);
		zone.deactivate(now);
		events.publishEvent(
				new DeliveryZoneStatusChanged(zone.getId(), false, currentActor.id(), currentActor.role(), now));
		return zone;
	}

	@Transactional
	public DeliveryZone reactivate(UUID id, long expectedVersion) {
		var zone = findOrThrow(id);
		requireMatchingVersion(zone, expectedVersion);
		if (zone.isActive()) {
			return zone;
		}
		var now = Instant.now(clock);
		zone.reactivate(now);
		events.publishEvent(
				new DeliveryZoneStatusChanged(zone.getId(), true, currentActor.id(), currentActor.role(), now));
		return zone;
	}

	private DeliveryZone findOrThrow(UUID id) {
		return zones.findById(id).orElseThrow(DeliveryZoneService::notFound);
	}

	// Short-circuits the common case; Hibernate's own version-guarded UPDATE at flush is the real race guard.
	private static void requireMatchingVersion(DeliveryZone zone, long expectedVersion) {
		if (zone.getVersion() != expectedVersion) {
			throw preconditionFailed(zone.getVersion());
		}
	}

	private void requireStoreCurrency(Money fee, @Nullable Money minimumOrder, @Nullable Money freeDeliveryThreshold) {
		var storeCurrency = Singletons.requireOne(settings.findAll()).getCurrency();
		if (!fee.currency().equals(storeCurrency) || (minimumOrder != null && !minimumOrder.currency().equals(storeCurrency))
				|| (freeDeliveryThreshold != null && !freeDeliveryThreshold.currency().equals(storeCurrency))) {
			throw new BusinessException(StoreError.DELIVERY_ZONE_CURRENCY_MISMATCH,
					"Delivery zone amounts must use the store currency " + storeCurrency.getCurrencyCode());
		}
	}

	private static BusinessException notFound() {
		return new BusinessException(StoreError.DELIVERY_ZONE_NOT_FOUND, "Delivery zone not found");
	}

	private static BusinessException nameAlreadyUsed() {
		return new BusinessException(StoreError.DELIVERY_ZONE_NAME_ALREADY_USED,
				"An active delivery zone with this name already exists");
	}

	private static BusinessException preconditionFailed(long currentVersion) {
		return new BusinessException(CommonError.PRECONDITION_FAILED, "If-Match does not match the current ETag",
				Map.of("currentETag", ETags.format(currentVersion)));
	}

}
