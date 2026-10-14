package com.jhanantezana.jugueria.notifications.internal;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
class StaffSetPasswordEmailTemplate {

	private static final String MESSAGE = "staff-set-password";

	private final EmailRenderer renderer;

	StaffSetPasswordEmailTemplate(EmailRenderer renderer) {
		this.renderer = renderer;
	}

	RenderedEmail render(URI link, Instant issuedAt, Instant expiresAt) {
		var hours = Duration.between(issuedAt, expiresAt).toHours();
		return renderer.render(MESSAGE, Locale.forLanguageTag("es"),
				Map.of("link", link.toString(), "expiresInHours", hours));
	}

}
