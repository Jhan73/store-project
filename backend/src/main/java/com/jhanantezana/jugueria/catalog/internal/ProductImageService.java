package com.jhanantezana.jugueria.catalog.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;

// Deliberately not transactional: the storage call is a remote call, so it must never hold a database connection.
// The version check and the key update are two short transactions of ProductService around it.
@Service
public class ProductImageService {

	private final ProductService products;

	private final ProductImageStorage storage;

	private final long maxBytes;

	ProductImageService(ProductService products, ProductImageStorage storage, CatalogProperties properties) {
		this.products = products;
		this.storage = storage;
		this.maxBytes = properties.images().maxSize().toBytes();
	}

	public ProductDetails replace(UUID productId, byte[] content, long expectedVersion) {
		if (content.length > maxBytes) {
			throw new BusinessException(CommonError.CONTENT_TOO_LARGE, "The image is larger than " + maxBytes + " bytes");
		}
		var format = ImageFormat.detect(content)
			.orElseThrow(() -> new BusinessException(CatalogError.INVALID_IMAGE,
					"The file is not a PNG, JPEG, or WebP image"));
		var key = "products/" + sha256(content) + "." + format.extension();

		var current = products.get(productId);
		EntityVersions.requireMatching(current.version(), expectedVersion);
		// The key is the content's hash: a product already pointing at it already has these exact bytes stored.
		if (key.equals(current.imageKey())) {
			return current;
		}
		storage.put(key, content, format.contentType());
		return products.setImage(productId, key, expectedVersion);
	}

	// The stored object stays: keys are shared by content, and another product may point at the same one.
	public ProductDetails remove(UUID productId, long expectedVersion) {
		return products.setImage(productId, null, expectedVersion);
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by the Java platform", e);
		}
	}

}
