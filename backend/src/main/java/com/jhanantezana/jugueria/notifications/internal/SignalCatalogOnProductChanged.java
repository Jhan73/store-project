package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.ProductChanged;
import com.jhanantezana.jugueria.notifications.RealtimeTopic;

// Duplicate delivery just resends an equivalent signal: harmless, so no dedup guard is needed.
@Component
class SignalCatalogOnProductChanged {

	private final AppEventsPublisher appEvents;

	SignalCatalogOnProductChanged(AppEventsPublisher appEvents) {
		this.appEvents = appEvents;
	}

	@ApplicationModuleListener
	void on(ProductChanged event) {
		appEvents.publish(RealtimeTopic.CATALOG, "PRODUCT_CHANGED", Map.of("productId", event.productId().toString()));
	}

}
