package com.jhanantezana.jugueria.catalog.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties("jugueria.catalog")
@Validated
public record CatalogProperties(@Valid @NotNull Images images) {

	// publicBaseUrl is where CloudFront serves the media bucket; an image's key is appended to it.
	public record Images(@NotBlank String publicBaseUrl) {
	}

}
