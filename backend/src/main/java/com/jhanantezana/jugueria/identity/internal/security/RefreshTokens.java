package com.jhanantezana.jugueria.identity.internal.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Public: identity.internal.RefreshTokenService generates and hashes tokens from outside this sub-package.
public final class RefreshTokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private RefreshTokens() {
	}

	public static String newRawToken() {
		var bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	// Never persist or log the raw token; only this hash is stored.
	public static String hash(String rawToken) {
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			// SHA-256 is a mandatory JDK algorithm.
			throw new IllegalStateException(e);
		}
	}

}
