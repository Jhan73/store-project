package com.jhanantezana.jugueria.notifications.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.shared.RealtimeSignalReceived;

class CloseSessionsOnSessionsRevokedTest {

	private final StompSessionRegistry sessions = mock(StompSessionRegistry.class);

	private final CloseSessionsOnSessionsRevoked listener = new CloseSessionsOnSessionsRevoked(sessions);

	@Test
	void closesTheSessionsOfTheUserNamedInTheSignal() {
		listener.on(new RealtimeSignalReceived("sessions", "USER_SESSIONS_REVOKED", Map.of("userId", "user-1")));

		verify(sessions).closeSessionsOf("user-1");
	}

	@Test
	void ignoresSignalsOfOtherTopics() {
		listener.on(new RealtimeSignalReceived("catalog", "USER_SESSIONS_REVOKED", Map.of("userId", "user-1")));

		verifyNoInteractions(sessions);
	}

	@Test
	void ignoresOtherTypesOnTheSessionsTopic() {
		listener.on(new RealtimeSignalReceived("sessions", "SOMETHING_ELSE", Map.of("userId", "user-1")));

		verifyNoInteractions(sessions);
	}

	@Test
	void ignoresASignalWithoutAUser() {
		listener.on(new RealtimeSignalReceived("sessions", "USER_SESSIONS_REVOKED", Map.of()));

		verifyNoInteractions(sessions);
	}

}
