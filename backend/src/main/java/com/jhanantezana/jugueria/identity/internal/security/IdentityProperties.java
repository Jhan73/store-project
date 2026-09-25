package com.jhanantezana.jugueria.identity.internal.security;

import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties("jugueria.identity")
@Validated
record IdentityProperties(@NotEmpty List<String> allowedOrigins, @Valid @NotNull Jwt jwt) {

	record Jwt(@NotBlank String issuer, @NotBlank String audience, @NotBlank String keyId,
			@Nullable String privateKey, boolean ephemeralKeyAllowed, @NotNull Duration accessTokenTtl) {
	}

}
