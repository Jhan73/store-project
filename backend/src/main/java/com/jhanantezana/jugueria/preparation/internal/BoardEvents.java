package com.jhanantezana.jugueria.preparation.internal;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.preparation.BoardChanged;

/** The signal that tells board screens to re-fetch; the facts other modules care about are published by the commands. */
@Component
class BoardEvents {

	private final ApplicationEventPublisher publisher;

	BoardEvents(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	void changed(String type, UUID boardOrderId, @Nullable UUID lineId, Instant now) {
		publisher.publishEvent(new BoardChanged(type, boardOrderId, lineId, now));
	}

}
