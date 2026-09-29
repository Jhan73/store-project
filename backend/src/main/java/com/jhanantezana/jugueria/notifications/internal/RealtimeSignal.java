package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;

// The NOTIFY payload shape: type + IDs only, never entities or personal data.
record RealtimeSignal(String topic, String type, Map<String, String> ids) {

	// What actually reaches STOMP subscribers: same as above minus "topic" (implied by the destination).
	// A record, not a Map, so field order in the serialized JSON is deterministic.
	record Outbound(String type, Map<String, String> ids) {
	}

	Outbound outbound() {
		return new Outbound(type, ids);
	}

}
