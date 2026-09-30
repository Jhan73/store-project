package com.jhanantezana.jugueria.catalog.internal;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.CatalogError;
import com.jhanantezana.jugueria.shared.BusinessException;

// Covers any context that is neither local, test, nor prod (e.g. the no-profile default tests run with).
@Component
@Profile("!local & !test & !prod")
class UnavailableProductImageStorage implements ProductImageStorage {

	@Override
	public void put(String key, byte[] content, String contentType) {
		throw new BusinessException(CatalogError.PROVIDER_UNAVAILABLE, "Image storage is not available");
	}

}
