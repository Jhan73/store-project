package com.jhanantezana.jugueria.notifications.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// The only adapter allowed to expose a set-password link: local development has no working email transport yet.
@Component
@Profile("local")
class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(EmailMessage message) {
		log.info("Email to {} — {}\n{}", message.to(), message.subject(), message.body());
	}

}
