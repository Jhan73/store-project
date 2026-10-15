package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;
import java.util.Set;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;
import com.jhanantezana.jugueria.preparation.BoardChanged;

// Duplicate delivery just resends an equivalent signal: harmless, so no dedup guard is needed.
@Component
class SignalDisplayOnBoardChanged {

	// What the customer screen shows (numbers, names, which column) does not move on these.
	private static final Set<String> NOT_ON_DISPLAY = Set.of("ORDER_STARTED", "LINE_READY", "LINE_FLAGGED",
			"MARKER_ACKNOWLEDGED");

	private final AppEventsPublisher appEvents;

	SignalDisplayOnBoardChanged(AppEventsPublisher appEvents) {
		this.appEvents = appEvents;
	}

	@ApplicationModuleListener
	void on(BoardChanged event) {
		if (!NOT_ON_DISPLAY.contains(event.type())) {
			appEvents.publish(RealtimeTopic.DISPLAY, "DISPLAY_CHANGED", Map.of());
		}
	}

}
