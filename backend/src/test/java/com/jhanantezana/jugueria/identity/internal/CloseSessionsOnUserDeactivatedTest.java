package com.jhanantezana.jugueria.identity.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.identity.UserDeactivated;
import com.jhanantezana.jugueria.notifications.NotificationsApi;
import com.jhanantezana.jugueria.shared.Role;

class CloseSessionsOnUserDeactivatedTest {

	private final NotificationsApi notifications = mock(NotificationsApi.class);

	@Test
	void revokesTheRealtimeSessionsOfTheDeactivatedUser() {
		var user = UUID.randomUUID();

		new CloseSessionsOnUserDeactivated(notifications)
			.on(new UserDeactivated(user, UUID.randomUUID(), Role.ADMIN, Instant.now()));

		verify(notifications).revokeSessions(user);
	}

}
