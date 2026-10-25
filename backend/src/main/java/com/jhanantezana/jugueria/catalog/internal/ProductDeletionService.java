package com.jhanantezana.jugueria.catalog.internal;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Deliberately not transactional: the storage call is a remote call, so it runs after the delete has committed.
@Service
public class ProductDeletionService {

	private static final Logger log = LoggerFactory.getLogger(ProductDeletionService.class);

	private final ProductService products;

	private final ProductImageStorage storage;

	ProductDeletionService(ProductService products, ProductImageStorage storage) {
		this.products = products;
		this.storage = storage;
	}

	public void delete(UUID id, long expectedVersion) {
		var imageKey = products.delete(id, expectedVersion);
		if (imageKey != null) {
			removeImageIfUnshared(imageKey);
		}
	}

	// Keys are content hashes, so another product may point at the same object: it stays while anyone does. A failure
	// only leaves an unreferenced object behind, which must never undo a delete that already committed.
	private void removeImageIfUnshared(String imageKey) {
		try {
			if (!products.imageKeyInUse(imageKey)) {
				storage.delete(imageKey);
			}
		}
		catch (RuntimeException e) {
			log.warn("The image object {} of a deleted product could not be removed", imageKey, e);
		}
	}

}
