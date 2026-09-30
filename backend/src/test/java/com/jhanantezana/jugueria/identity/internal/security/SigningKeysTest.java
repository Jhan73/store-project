package com.jhanantezana.jugueria.identity.internal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class SigningKeysTest {

	@Test
	void readsAPkcs8PrivateKeyAndDerivesItsPublicKey() throws Exception {
		var generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		var pair = generator.generateKeyPair();

		var key = SigningKeys.rsaKey("2026-09", pem("PRIVATE KEY", pair.getPrivate().getEncoded()), false);

		assertThat(key.getKeyID()).isEqualTo("2026-09");
		assertThat(key.toRSAPublicKey().getModulus()).isEqualTo(((RSAPublicKey) pair.getPublic()).getModulus());
		assertThat(key.isPrivate()).isTrue();
	}

	@Test
	void rejectsAPkcs1PrivateKey() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> SigningKeys.rsaKey("2026-09", pem("RSA PRIVATE KEY", new byte[] { 1, 2, 3 }), false));
	}

	@Test
	void generatesAnEphemeralKeyOnlyWhenAllowed() throws Exception {
		var key = SigningKeys.rsaKey("local", null, true);

		assertThat(key.isPrivate()).isTrue();
		assertThat(key.size()).isEqualTo(2048);
	}

	@Test
	void refusesToStartWithoutAKeyWhereEphemeralKeysAreNotAllowed() {
		assertThatIllegalStateException().isThrownBy(() -> SigningKeys.rsaKey("2026-09", null, false));
		// An SSM parameter that exists but is empty resolves the placeholder to a blank value.
		assertThatIllegalStateException().isThrownBy(() -> SigningKeys.rsaKey("2026-09", "  ", false));
	}

	private static String pem(String type, byte[] der) {
		var body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
		return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
	}

}
