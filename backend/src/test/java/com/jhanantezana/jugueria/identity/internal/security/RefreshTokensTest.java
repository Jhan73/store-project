package com.jhanantezana.jugueria.identity.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokensTest {

	@Test
	void generatesHighEntropyUniqueRawTokens() {
		var first = RefreshTokens.newRawToken();
		var second = RefreshTokens.newRawToken();

		assertThat(first).isNotEqualTo(second);
		assertThat(first.length()).isGreaterThanOrEqualTo(32);
	}

	@Test
	void hashesTheSameTokenToTheSameValue() {
		var raw = RefreshTokens.newRawToken();

		assertThat(RefreshTokens.hash(raw)).isEqualTo(RefreshTokens.hash(raw));
	}

	@Test
	void hashesDifferentTokensToDifferentValues() {
		var first = RefreshTokens.newRawToken();
		var second = RefreshTokens.newRawToken();

		assertThat(RefreshTokens.hash(first)).isNotEqualTo(RefreshTokens.hash(second));
	}

	@Test
	void hashIsALowercaseSha256HexString() {
		var hash = RefreshTokens.hash("fixed-value-for-this-test");

		assertThat(hash).hasSize(64).matches("[0-9a-f]+");
	}

}
