package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class NotificationsPropertiesTest {

	@Test
	void treatsAMissingListAsEmpty() {
		var properties = new NotificationsProperties("no-reply@jugueria.jhanantezana.com", null,
				new NotificationsProperties.Ses("us-east-1"));

		assertThat(properties.recipientAllowlist()).isEmpty();
	}

	@Test
	void dropsBlankEntriesLeftByAnUnsetEnvironmentVariable() {
		var properties = new NotificationsProperties("no-reply@jugueria.jhanantezana.com", Arrays.asList(""),
				new NotificationsProperties.Ses("us-east-1"));

		assertThat(properties.recipientAllowlist()).isEmpty();
	}

	@Test
	void keepsRealEntries() {
		var properties = new NotificationsProperties("no-reply@jugueria.jhanantezana.com",
				List.of("owner@jugueria.pe"), new NotificationsProperties.Ses("us-east-1"));

		assertThat(properties.recipientAllowlist()).containsExactly("owner@jugueria.pe");
	}

}
