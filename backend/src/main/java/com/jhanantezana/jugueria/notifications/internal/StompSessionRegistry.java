package com.jhanantezana.jugueria.notifications.internal;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * The WebSocket sessions of this task and the user each one authenticated as, so a deactivated user's sessions can
 * be closed. A STOMP session id is the WebSocket session id.
 */
@Component
class StompSessionRegistry {

	private static final Logger log = LoggerFactory.getLogger(StompSessionRegistry.class);

	private static final CloseStatus USER_DEACTIVATED = CloseStatus.POLICY_VIOLATION.withReason("User deactivated");

	private final Map<String, WebSocketSession> sockets = new ConcurrentHashMap<>();

	private final Map<String, String> userBySession = new ConcurrentHashMap<>();

	// When the account behind each authenticated session is next due for a lookup.
	private final Map<String, Instant> recheckAtBySession = new ConcurrentHashMap<>();

	void opened(WebSocketSession session) {
		sockets.put(session.getId(), session);
	}

	void authenticated(String sessionId, String userId, Instant recheckAt) {
		if (sockets.containsKey(sessionId)) {
			userBySession.put(sessionId, userId);
			recheckAtBySession.put(sessionId, recheckAt);
		}
	}

	void closed(String sessionId) {
		sockets.remove(sessionId);
		userBySession.remove(sessionId);
		recheckAtBySession.remove(sessionId);
	}

	/** The users with at least one session whose account lookup is due; each is listed once. */
	List<String> usersDue(Instant now) {
		return recheckAtBySession.entrySet()
			.stream()
			.filter(entry -> !entry.getValue().isAfter(now))
			.map(entry -> userBySession.get(entry.getKey()))
			.filter(userId -> userId != null)
			.distinct()
			.toList();
	}

	void rechecked(String userId, Instant nextRecheckAt) {
		for (var entry : userBySession.entrySet()) {
			if (entry.getValue().equals(userId)) {
				recheckAtBySession.put(entry.getKey(), nextRecheckAt);
			}
		}
	}

	void closeSessionsOf(String userId) {
		for (var entry : userBySession.entrySet()) {
			if (!entry.getValue().equals(userId)) {
				continue;
			}
			var socket = sockets.get(entry.getKey());
			closed(entry.getKey());
			if (socket == null) {
				continue;
			}
			try {
				socket.close(USER_DEACTIVATED);
			}
			catch (IOException | RuntimeException e) {
				// The client may already be gone; the remaining sessions of the user still have to close.
				log.debug("Could not close session {} of a deactivated user", entry.getKey(), e);
			}
		}
	}

}
