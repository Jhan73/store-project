package com.jhanantezana.jugueria.catalog.internal.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.jhanantezana.jugueria.catalog.internal.CatalogProperties;

class S3ClientConfigurationTest {

	@Test
	void buildsAClientForTheConfiguredRegionWithoutStoredCredentials() {
		var images = new CatalogProperties.Images("https://media.example", "bucket", "sa-east-1", Duration.ofSeconds(10),
				Duration.ofSeconds(5), DataSize.ofMegabytes(2));

		try (var client = new S3ClientConfiguration().s3Client(new CatalogProperties(images))) {
			assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("sa-east-1");
		}
	}

	@Test
	void appliesTheConfiguredTimeouts() {
		var images = new CatalogProperties.Images("https://media.example", "bucket", "us-east-1", Duration.ofSeconds(10),
				Duration.ofSeconds(5), DataSize.ofMegabytes(2));

		var override = S3ClientConfiguration.overrideConfiguration(images);

		assertThat(override.apiCallTimeout()).contains(Duration.ofSeconds(10));
		assertThat(override.apiCallAttemptTimeout()).contains(Duration.ofSeconds(5));
	}

}
