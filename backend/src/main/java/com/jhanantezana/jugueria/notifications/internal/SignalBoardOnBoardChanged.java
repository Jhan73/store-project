package com.jhanantezana.jugueria.notifications.internal;

import java.util.LinkedHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;
import com.jhanantezana.jugueria.preparation.BoardChanged;

// Not an @ApplicationModuleListener: the board must hear about a change right after commit, not when the registry
// next gets to it. Clients re-read on any signal, so a lost or repeated one is harmless.
@Component
class SignalBoardOnBoardChanged {

	private final AfterCommitSignals signals;

	SignalBoardOnBoardChanged(AfterCommitSignals signals) {
		this.signals = signals;
	}

	@EventListener
	void on(BoardChanged event) {
		var ids = new LinkedHashMap<String, String>();
		ids.put("orderId", event.boardOrderId().toString());
		if (event.lineId() != null) {
			ids.put("lineId", event.lineId().toString());
		}
		signals.publish(RealtimeTopic.BOARD, event.type(), ids);
	}

}
