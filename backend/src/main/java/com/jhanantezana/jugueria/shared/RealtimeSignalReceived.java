package com.jhanantezana.jugueria.shared;

import java.util.Map;

/**
 * A signal that arrived over the LISTEN/NOTIFY bridge. Every task publishes it locally, which is how a module
 * reacts once per task (evicting an in-process cache) without depending on {@code notifications}.
 */
public record RealtimeSignalReceived(String topic, String type, Map<String, String> ids) {
}
