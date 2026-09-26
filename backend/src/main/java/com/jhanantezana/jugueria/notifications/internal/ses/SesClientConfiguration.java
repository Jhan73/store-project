package com.jhanantezana.jugueria.notifications.internal.ses;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.jhanantezana.jugueria.notifications.internal.NotificationsProperties;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration(proxyBeanMethods = false)
@Profile({ "test", "prod" })
class SesClientConfiguration {

	// The task role: no stored credentials.
	@Bean
	SesV2Client sesV2Client(NotificationsProperties properties) {
		return SesV2Client.builder()
			.region(Region.of(properties.ses().region()))
			.credentialsProvider(DefaultCredentialsProvider.builder().build())
			.overrideConfiguration(overrideConfiguration(properties.ses()))
			.build();
	}

	static ClientOverrideConfiguration overrideConfiguration(NotificationsProperties.Ses ses) {
		return ClientOverrideConfiguration.builder()
			.apiCallTimeout(ses.apiCallTimeout())
			.apiCallAttemptTimeout(ses.apiCallAttemptTimeout())
			.build();
	}

}
