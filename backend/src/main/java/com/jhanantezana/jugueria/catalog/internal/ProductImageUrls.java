package com.jhanantezana.jugueria.catalog.internal;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ProductImageUrls {

	private final String baseUrl;

	public ProductImageUrls(CatalogProperties properties) {
		var base = properties.images().publicBaseUrl();
		this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	public @Nullable String urlFor(@Nullable String imageKey) {
		return imageKey == null ? null : baseUrl + "/" + imageKey;
	}

}
