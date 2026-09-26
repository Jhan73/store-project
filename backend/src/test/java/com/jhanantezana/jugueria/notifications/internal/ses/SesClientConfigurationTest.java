package com.jhanantezana.jugueria.notifications.internal.ses;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.notifications.internal.NotificationsProperties;

class SesClientConfigurationTest {

	@Test
	void appliesTheConfiguredTimeouts() {
		var ses = new NotificationsProperties.Ses("us-east-1", Duration.ofSeconds(10), Duration.ofSeconds(5));

		var override = SesClientConfiguration.overrideConfiguration(ses);

		assertThat(override.apiCallTimeout()).contains(Duration.ofSeconds(10));
		assertThat(override.apiCallAttemptTimeout()).contains(Duration.ofSeconds(5));
	}

}
