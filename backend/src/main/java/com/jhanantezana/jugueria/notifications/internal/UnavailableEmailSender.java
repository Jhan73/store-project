package com.jhanantezana.jugueria.notifications.internal;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Covers any context that is neither local, test, nor prod (e.g. the no-profile default tests run with).
@Component
@Profile("!local & !test & !prod")
class UnavailableEmailSender implements EmailSender {

	@Override
	public void send(EmailMessage message) {
		throw new IllegalStateException("Email transport is not available in this profile");
	}

}
