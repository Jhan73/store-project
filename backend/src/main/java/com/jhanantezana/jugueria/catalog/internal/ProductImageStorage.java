package com.jhanantezana.jugueria.catalog.internal;

// The port to object storage. Public within internal/ so the s3 adapter sub-package can implement it.
public interface ProductImageStorage {

	/** Stores the object under a content-addressed key; a repeated put of the same key and bytes is harmless. */
	void put(String key, byte[] content, String contentType);

}
