package com.jhanantezana.jugueria.identity.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.UserRoleChanged;
import com.jhanantezana.jugueria.notifications.NotificationsApi;

// A session is authorized once, at SUBSCRIBE, so a demoted user would keep the topics of the old role until the socket
// dropped. Closing a session twice is a no-op, so a redelivered event is harmless.
@Component
class CloseSessionsOnUserRoleChanged {

	private final NotificationsApi notifications;

	CloseSessionsOnUserRoleChanged(NotificationsApi notifications) {
		this.notifications = notifications;
	}

	@ApplicationModuleListener
	void on(UserRoleChanged event) {
		notifications.revokeSessions(event.userId());
	}

}
