package com.jhanantezana.jugueria.catalog.internal.s3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.catalog.internal.CatalogProperties;
import com.jhanantezana.jugueria.catalog.internal.ProductImageStorage;
import com.jhanantezana.jugueria.shared.BusinessException;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@Profile({ "local", "test", "prod" })
class S3ProductImageStorage implements ProductImageStorage {

	private static final Logger log = LoggerFactory.getLogger(S3ProductImageStorage.class);

	// The key is a hash of the content, so the object never changes and can be cached for good.
	private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

	private final S3Client client;

	private final String bucket;

	S3ProductImageStorage(S3Client client, CatalogProperties properties) {
		this.client = client;
		this.bucket = properties.images().bucket();
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		var request = PutObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.contentType(contentType)
			.cacheControl(CACHE_CONTROL)
			.build();
		try {
			client.putObject(request, RequestBody.fromBytes(content));
		}
		catch (SdkException e) {
			log.error("Storing the object {} failed", key, e);
			throw new BusinessException(CatalogError.PROVIDER_UNAVAILABLE, "Image storage is unavailable");
		}
	}

}
