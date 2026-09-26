package com.jhanantezana.jugueria.notifications.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

// No text in Java: the subject and body live in this template file (tech-spec §6.6), read at render time.
// The full engine decision (M2-B6) is still open; plain substitution is the simplest compliant option until then.
@Component
class StaffSetPasswordEmailTemplate {

	private static final String RESOURCE = "notifications/staff-set-password-email.txt";

	private static final String SUBJECT_PREFIX = "Subject: ";

	RenderedEmail render(URI link, Instant issuedAt, Instant expiresAt) {
		var raw = readResource();
		var hours = Duration.between(issuedAt, expiresAt).toHours();
		var filled = raw.replace("{{link}}", link.toString()).replace("{{expiresInHours}}", Long.toString(hours));
		var parts = filled.split("\\R", 2);
		var subject = parts[0].replaceFirst("^" + SUBJECT_PREFIX, "").strip();
		var body = parts.length > 1 ? parts[1].strip() : "";
		return new RenderedEmail(subject, body);
	}

	private static String readResource() {
		try {
			return StreamUtils.copyToString(new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Missing email template " + RESOURCE, e);
		}
	}

}
