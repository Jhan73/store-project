package com.jhanantezana.jugueria.store.internal;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.store.DeliveryZoneView;
import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.ReasonView;
import com.jhanantezana.jugueria.store.StoreApi;
import com.jhanantezana.jugueria.store.StoreThresholds;

@Service
class StoreApiImpl implements StoreApi {

	private final StoreSettingsRepository settings;

	private final OpeningHourRepository openingHours;

	private final DeliveryZoneRepository deliveryZones;

	private final ReasonRepository reasons;

	StoreApiImpl(StoreSettingsRepository settings, OpeningHourRepository openingHours,
			DeliveryZoneRepository deliveryZones, ReasonRepository reasons) {
		this.settings = settings;
		this.openingHours = openingHours;
		this.deliveryZones = deliveryZones;
		this.reasons = reasons;
	}

	@Override
	@Transactional(readOnly = true)
	public ZoneId timeZone() {
		return singleton().zoneId();
	}

	@Override
	@Transactional(readOnly = true)
	public Currency currency() {
		return singleton().getCurrency();
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isOpenAt(Instant instant) {
		return OpeningHoursCalculator.isOpenAt(singleton().zoneId(), openingHours.findAll(), instant);
	}

	@Override
	@Transactional(readOnly = true)
	public StoreThresholds thresholds() {
		var current = singleton();
		return new StoreThresholds(current.getBasePrepMinutes(), current.getQueueMinutesPerOrder(),
				current.getBusyModeMinutes(), current.getBoardWarningMinutes(), current.getBoardLateMinutes(),
				current.getRegisterDifferenceThreshold(), current.getExceptionThreshold(),
				current.getOnlineCapacityLimit());
	}

	@Override
	@Transactional(readOnly = true)
	public List<ReasonView> reasons(ReasonType type) {
		return reasons.findByType(type)
			.stream()
			.map(reason -> new ReasonView(reason.getId(), reason.getType(), reason.getCode(), reason.isActive()))
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<DeliveryZoneView> deliveryZone(UUID zoneId) {
		return deliveryZones.findById(zoneId)
			.map(zone -> new DeliveryZoneView(zone.getId(), zone.getName(), zone.getFee(), zone.getDeliveryMinutes(),
					zone.getMinimumOrder(), zone.getFreeDeliveryThreshold(), zone.isActive()));
	}

	private StoreSettings singleton() {
		return settings.findAll().getFirst();
	}

}
