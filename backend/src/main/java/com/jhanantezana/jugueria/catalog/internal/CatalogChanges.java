package com.jhanantezana.jugueria.catalog.internal;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

// The one door every catalog command publishes its change through.
@Component
class CatalogChanges {

	private final ApplicationEventPublisher events;

	CatalogChanges(ApplicationEventPublisher events) {
		this.events = events;
	}

	void publish(Object event) {
		events.publishEvent(event);
	}

}
