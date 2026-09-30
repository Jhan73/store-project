package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.AvailabilityChanged;
import com.jhanantezana.jugueria.notifications.RealtimeTopic;

// Duplicate delivery just resends an equivalent signal: harmless, so no dedup guard is needed.
@Component
class SignalCatalogOnAvailabilityChanged {

	private final AppEventsPublisher appEvents;

	SignalCatalogOnAvailabilityChanged(AppEventsPublisher appEvents) {
		this.appEvents = appEvents;
	}

	@ApplicationModuleListener
	void on(AvailabilityChanged event) {
		var id = event.targetId().toString();
		switch (event.target()) {
			case PRODUCT -> appEvents.publish(RealtimeTopic.CATALOG, "PRODUCT_AVAILABILITY_CHANGED",
					Map.of("productId", id));
			case MODIFIER_OPTION -> appEvents.publish(RealtimeTopic.CATALOG, "OPTION_AVAILABILITY_CHANGED",
					Map.of("optionId", id));
		}
	}

}
