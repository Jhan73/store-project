package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class StaffSetPasswordEmailTemplateTest {

	static final Instant NOW = Instant.parse("2026-09-26T09:00:00Z");

	final StaffSetPasswordEmailTemplate template = new StaffSetPasswordEmailTemplate();

	@Test
	void rendersTheLinkAndTheExpiryIntoTheBodyWithNoTextInJava() {
		var link = URI.create("https://jugueria.jhanantezana.com/set-password?token=abc123");

		var rendered = template.render(link, NOW, NOW.plus(Duration.ofHours(48)));

		assertThat(rendered.subject()).isNotBlank();
		assertThat(rendered.body()).contains(link.toString()).contains("48");
	}

}
