package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;
import com.jhanantezana.jugueria.preparation.BoardChanged;

// Not an @ApplicationModuleListener, for the same reason as SignalBoardOnBoardChanged.
@Component
class SignalDisplayOnBoardChanged {

	// What the customer screen shows (numbers, names, which column) does not move on these.
	private static final Set<String> NOT_ON_DISPLAY = Set.of("ORDER_STARTED", "LINE_READY", "LINE_FLAGGED",
			"MARKER_ACKNOWLEDGED");

	private final AfterCommitSignals signals;

	SignalDisplayOnBoardChanged(AfterCommitSignals signals) {
		this.signals = signals;
	}

	@EventListener
	void on(BoardChanged event) {
		if (!NOT_ON_DISPLAY.contains(event.type())) {
			signals.publish(RealtimeTopic.DISPLAY, "DISPLAY_CHANGED", Map.of());
		}
	}

}
