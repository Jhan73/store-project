package com.jhanantezana.jugueria.notifications.internal;

import java.util.Map;

// The NOTIFY payload shape: type + IDs only, never entities or personal data (tech-spec §4.4).
record RealtimeSignal(String topic, String type, Map<String, String> ids) {
}
