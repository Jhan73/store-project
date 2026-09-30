package com.jhanantezana.jugueria.identity.web;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

// __Host- pins the cookie to this exact origin: no Domain attribute, Path=/, Secure required.
final class RefreshTokenCookie {

	static final String NAME = "__Host-refresh-token";

	private RefreshTokenCookie() {
	}

	static ResponseCookie issued(String rawToken, Duration maxAge) {
		return build(rawToken, maxAge);
	}

	static ResponseCookie cleared() {
		return build("", Duration.ZERO);
	}

	private static ResponseCookie build(String value, Duration maxAge) {
		return ResponseCookie.from(NAME, value)
			.httpOnly(true)
			.secure(true)
			.sameSite("Strict")
			.path("/")
			.maxAge(maxAge)
			.build();
	}

}
