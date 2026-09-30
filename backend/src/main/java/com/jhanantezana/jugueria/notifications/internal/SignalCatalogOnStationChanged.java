package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.StationChanged;
import com.jhanantezana.jugueria.notifications.RealtimeTopic;

// Duplicate delivery just resends an equivalent signal: harmless, so no dedup guard is needed.
@Component
class SignalCatalogOnStationChanged {

	private final AppEventsPublisher appEvents;

	SignalCatalogOnStationChanged(AppEventsPublisher appEvents) {
		this.appEvents = appEvents;
	}

	@ApplicationModuleListener
	void on(StationChanged event) {
		appEvents.publish(RealtimeTopic.CATALOG, "STATION_CHANGED", Map.of("stationId", event.stationId().toString()));
	}

}
