package com.jhanantezana.jugueria.notifications.internal;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Fails loudly instead of pretending to deliver, and logs nothing so no address or content leaks.
@Component
@Profile("!local")
class UnavailableEmailSender implements EmailSender {

	@Override
	public void send(EmailMessage message) {
		throw new IllegalStateException("Email transport is not available yet");
	}

}
