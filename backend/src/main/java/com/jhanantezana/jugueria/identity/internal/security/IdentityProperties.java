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
import jakarta.validation.constraints.Positive;

// Public: identity.internal (LoginService) reads the lockout settings from outside this sub-package.
@ConfigurationProperties("jugueria.identity")
@Validated
public record IdentityProperties(@NotEmpty List<String> allowedOrigins, @Valid @NotNull Jwt jwt,
		@Valid @NotNull Lockout lockout) {

	public record Jwt(@NotBlank String issuer, @NotBlank String audience, @NotBlank String keyId,
			@Nullable String privateKey, boolean ephemeralKeyAllowed, @NotNull Duration accessTokenTtl) {
	}

	public record Lockout(@Positive int maxFailedAttempts, @NotNull Duration lockoutDuration) {
	}

}
