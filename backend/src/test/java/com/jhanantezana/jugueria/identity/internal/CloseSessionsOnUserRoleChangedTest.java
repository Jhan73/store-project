package com.jhanantezana.jugueria.identity.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.identity.UserRoleChanged;
import com.jhanantezana.jugueria.notifications.NotificationsApi;
import com.jhanantezana.jugueria.shared.Role;

class CloseSessionsOnUserRoleChangedTest {

	private final NotificationsApi notifications = mock(NotificationsApi.class);

	@Test
	void revokesTheRealtimeSessionsOfTheUserWhoseRoleChanged() {
		var user = UUID.randomUUID();

		new CloseSessionsOnUserRoleChanged(notifications)
			.on(new UserRoleChanged(user, Role.SERVER, Role.KITCHEN, UUID.randomUUID(), Role.ADMIN, Instant.now()));

		verify(notifications).revokeSessions(user);
	}

}
