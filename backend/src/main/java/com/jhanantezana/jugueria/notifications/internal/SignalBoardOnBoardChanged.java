package com.jhanantezana.jugueria.notifications.internal;

import java.util.LinkedHashMap;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;
import com.jhanantezana.jugueria.preparation.BoardChanged;

// Duplicate delivery just resends an equivalent signal: harmless, so no dedup guard is needed.
@Component
class SignalBoardOnBoardChanged {

	private final AppEventsPublisher appEvents;

	SignalBoardOnBoardChanged(AppEventsPublisher appEvents) {
		this.appEvents = appEvents;
	}

	@ApplicationModuleListener
	void on(BoardChanged event) {
		var ids = new LinkedHashMap<String, String>();
		ids.put("orderId", event.boardOrderId().toString());
		if (event.lineId() != null) {
			ids.put("lineId", event.lineId().toString());
		}
		appEvents.publish(RealtimeTopic.BOARD, event.type(), ids);
	}

}
