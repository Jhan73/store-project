package com.jhanantezana.jugueria.notifications.internal;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// No SMTP/SES dependency is approved yet (tech-spec §3): fails loudly instead of pretending to deliver.
// Never logs the recipient, subject, or body — callers must not either.
@Component
@Profile("!local")
class UnavailableEmailSender implements EmailSender {

	@Override
	public void send(EmailMessage message) {
		throw new IllegalStateException(
				"Email transport is not available: spring-boot-starter-mail/SES adapter needs owner approval (tech-spec §3)");
	}

}
