package com.jhanantezana.jugueria.catalog.internal.s3;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.internal.CatalogProperties;
import com.jhanantezana.jugueria.shared.BusinessException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

// S3 has no local emulator here; the SDK's endpoint override points the client at WireMock instead.
class S3ProductImageStorageTest {

	static final String BUCKET = "jugueria-test-media";

	static final byte[] IMAGE = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4 };

	WireMockServer wireMock;

	S3Client client;

	@BeforeEach
	void setUp() {
		wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
		wireMock.start();
		client = S3Client.builder()
			.region(Region.US_EAST_1)
			.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
			.endpointOverride(URI.create(wireMock.baseUrl()))
			.forcePathStyle(true)
			// Plain request bodies, so the test can compare the bytes; the trailer checksum is the SDK's own concern.
			.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
			.overrideConfiguration(override -> override.retryStrategy(retry -> retry.maxAttempts(1)))
			.build();
	}

	@AfterEach
	void tearDown() {
		client.close();
		wireMock.stop();
	}

	private S3ProductImageStorage storage() {
		var images = new CatalogProperties.Images("https://media.example", BUCKET, "us-east-1", Duration.ofSeconds(10),
				Duration.ofSeconds(5), DataSize.ofMegabytes(2));
		return new S3ProductImageStorage(client, new CatalogProperties(images));
	}

	@Test
	void storesTheImageWithItsContentTypeAndALongImmutableCacheControl() {
		wireMock.stubFor(put(urlEqualTo("/" + BUCKET + "/products/abc.png"))
			.willReturn(aResponse().withStatus(200).withHeader("ETag", "\"etag\"")));

		storage().put("products/abc.png", IMAGE, "image/png");

		wireMock.verify(putRequestedFor(urlEqualTo("/" + BUCKET + "/products/abc.png"))
			.withHeader("Content-Type", equalTo("image/png"))
			.withHeader("Cache-Control", equalTo("public, max-age=31536000, immutable")));
		// Over plain HTTP the SDK signs the payload in chunks, so the bytes arrive framed rather than bare.
		var body = wireMock.getAllServeEvents().getFirst().getRequest().getBody();
		assertThat(indexOf(body, IMAGE)).isGreaterThanOrEqualTo(0);
	}

	private static int indexOf(byte[] haystack, byte[] needle) {
		for (var i = 0; i <= haystack.length - needle.length; i++) {
			if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) {
				return i;
			}
		}
		return -1;
	}

	@Test
	void reportsAProviderFailureAsUnavailableWithoutLeakingItsDetail() {
		wireMock.stubFor(put(urlEqualTo("/" + BUCKET + "/products/abc.png"))
			.willReturn(aResponse().withStatus(500).withBody("<Error><Message>internal detail</Message></Error>")));

		assertThatThrownBy(() -> storage().put("products/abc.png", IMAGE, "image/png"))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.errorCode()).isEqualTo(CatalogError.PROVIDER_UNAVAILABLE);
				assertThat(e.getMessage()).doesNotContain("internal detail");
			});
	}

	@Test
	void reportsAnUnreachableProviderAsUnavailable() {
		wireMock.stop();

		assertThatThrownBy(() -> storage().put("products/abc.png", IMAGE, "image/png"))
			.isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(CatalogError.PROVIDER_UNAVAILABLE));
	}

}
