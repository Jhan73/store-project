package com.jhanantezana.jugueria.identity.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.UserDeactivated;
import com.jhanantezana.jugueria.notifications.NotificationsApi;

// Closing a session twice is a no-op, so a redelivered event is harmless.
@Component
class CloseSessionsOnUserDeactivated {

	private final NotificationsApi notifications;

	CloseSessionsOnUserDeactivated(NotificationsApi notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(UserDeactivated event) {
		notifications.revokeSessions(event.userId());
	}

}
