package com.jhanantezana.jugueria.catalog.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties("jugueria.catalog")
@Validated
public record CatalogProperties(@Valid @NotNull Images images) {

	// publicBaseUrl is where CloudFront serves the bucket; an image's key is appended to it.
	public record Images(@NotBlank String publicBaseUrl, @NotBlank String bucket, @NotBlank String region,
			@NotNull Duration apiCallTimeout, @NotNull Duration apiCallAttemptTimeout,
			@NotNull @DefaultValue("2MB") DataSize maxSize) {
	}

}
