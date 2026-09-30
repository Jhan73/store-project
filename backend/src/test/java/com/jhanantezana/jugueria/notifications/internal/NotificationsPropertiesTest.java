package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class NotificationsPropertiesTest {

	static final NotificationsProperties.Ses SES = new NotificationsProperties.Ses("us-east-1", Duration.ofSeconds(10),
			Duration.ofSeconds(5));

	@Test
	void treatsAMissingListAsEmpty() {
		var properties = new NotificationsProperties("no-reply@jugueria.jhanantezana.com", null, SES,
				"jugueria-backend");

		assertThat(properties.recipientAllowlist()).isEmpty();
	}

	@Test
	void dropsBlankEntriesLeftByAnUnsetEnvironmentVariable() {
		var properties = new NotificationsProperties("no-reply@jugueria.jhanantezana.com", Arrays.asList(""), SES,
				"jugueria-backend");

		assertThat(properties.recipientAllowlist()).isEmpty();
	}

	@Test
	void keepsRealEntries() {
		var properties = new NotificationsProperties("no-reply@jugueria.jhanantezana.com",
				List.of("owner@jugueria.pe"), SES, "jugueria-backend");

		assertThat(properties.recipientAllowlist()).containsExactly("owner@jugueria.pe");
	}

}
