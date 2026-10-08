package com.jhanantezana.jugueria.identity.internal.security;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Public: identity.internal (LoginService) reads the lockout settings from outside this sub-package.
@ConfigurationProperties("jugueria.identity")
@Validated
public record IdentityProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Lockout lockout,
		@Valid @NotNull RefreshToken refreshToken, @Valid @NotNull SetPassword setPassword) {

	public record Jwt(@NotBlank String issuer, @NotBlank String audience, @NotBlank String keyId,
			@Nullable String privateKey, boolean ephemeralKeyAllowed, @NotNull Duration accessTokenTtl) {
	}

	public record Lockout(@Positive int maxFailedAttempts, @NotNull Duration lockoutDuration) {
	}

	public record RefreshToken(@NotNull Duration idleTtl, @NotNull Duration absoluteTtl) {
	}

	public record SetPassword(@NotBlank String frontendBaseUrl, @NotBlank String frontendPath,
			@NotNull Duration tokenTtl) {
	}

}
