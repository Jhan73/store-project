package com.jhanantezana.jugueria.notifications.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.notifications.RealtimeTopic;
import com.jhanantezana.jugueria.shared.RealtimeSignalReceived;

// Runs on every task: each one closes the sessions it holds. Closing an already-closed session is a no-op.
@Component
class CloseSessionsOnSessionsRevoked {

	static final String TYPE = "USER_SESSIONS_REVOKED";

	private final StompSessionRegistry sessions;

	CloseSessionsOnSessionsRevoked(StompSessionRegistry sessions) {
		this.sessions = sessions;
	}

	@EventListener
	void on(RealtimeSignalReceived signal) {
		if (!RealtimeTopic.SESSIONS.channel().equals(signal.topic()) || !TYPE.equals(signal.type())) {
			return;
		}
		var userId = signal.ids().get("userId");
		if (userId != null) {
			sessions.closeSessionsOf(userId);
		}
	}

}
