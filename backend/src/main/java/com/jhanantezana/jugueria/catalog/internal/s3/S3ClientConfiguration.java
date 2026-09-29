package com.jhanantezana.jugueria.catalog.internal.s3;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.jhanantezana.jugueria.catalog.internal.CatalogProperties;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@Profile({ "local", "test", "prod" })
class S3ClientConfiguration {

	// The task role in test and prod, the short-lived SSO profile locally: never stored credentials.
	@Bean
	S3Client s3Client(CatalogProperties properties) {
		var images = properties.images();
		return S3Client.builder()
			.region(Region.of(images.region()))
			.credentialsProvider(DefaultCredentialsProvider.builder().build())
			.overrideConfiguration(overrideConfiguration(images))
			.build();
	}

	static ClientOverrideConfiguration overrideConfiguration(CatalogProperties.Images images) {
		return ClientOverrideConfiguration.builder()
			.apiCallTimeout(images.apiCallTimeout())
			.apiCallAttemptTimeout(images.apiCallAttemptTimeout())
			.build();
	}

}
