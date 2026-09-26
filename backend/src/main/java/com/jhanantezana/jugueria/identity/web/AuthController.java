package com.jhanantezana.jugueria.identity.web;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.identity.internal.LoginService;
import com.jhanantezana.jugueria.identity.internal.RefreshTokenService;
import com.jhanantezana.jugueria.identity.internal.security.IdentityProperties;

import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final LoginService loginService;

	private final RefreshTokenService refreshTokenService;

	private final IdentityProperties.RefreshToken refreshTokenProperties;

	AuthController(LoginService loginService, RefreshTokenService refreshTokenService, IdentityProperties properties) {
		this.loginService = loginService;
		this.refreshTokenService = refreshTokenService;
		this.refreshTokenProperties = properties.refreshToken();
	}

	@PostMapping("/login")
	@PermitAll
	ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		var result = loginService.login(request.email(), request.password());
		return withRefreshCookie(LoginResponse.from(result), result.refreshToken());
	}

	// X-Requested-With is required so a browser must run a CORS preflight before this cookie-authenticated call.
	@PostMapping("/refresh")
	@PermitAll
	ResponseEntity<LoginResponse> refresh(
			@CookieValue(value = RefreshTokenCookie.NAME, required = false) @Nullable String refreshToken,
			@RequestHeader("X-Requested-With") String requestedWith) {
		var result = refreshTokenService.rotate(refreshToken);
		return withRefreshCookie(LoginResponse.from(result), result.refreshToken());
	}

	@PostMapping("/logout")
	@PermitAll
	ResponseEntity<Void> logout(
			@CookieValue(value = RefreshTokenCookie.NAME, required = false) @Nullable String refreshToken,
			@RequestHeader("X-Requested-With") String requestedWith) {
		refreshTokenService.logout(refreshToken);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.cleared().toString()).build();
	}

	private ResponseEntity<LoginResponse> withRefreshCookie(LoginResponse body, String rawRefreshToken) {
		var cookie = RefreshTokenCookie.issued(rawRefreshToken, refreshTokenProperties.ttl());
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
	}

}
