package com.jhanantezana.jugueria.identity.internal.security;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.converter.RsaKeyConverters;

import com.nimbusds.jose.jwk.RSAKey;

final class SigningKeys {

	private static final Logger log = LoggerFactory.getLogger(SigningKeys.class);

	private SigningKeys() {
	}

	static RSAKey rsaKey(String keyId, @Nullable String privateKeyPem, boolean ephemeralAllowed) {
		try {
			if (privateKeyPem == null || privateKeyPem.isBlank()) {
				if (!ephemeralAllowed) {
					throw new IllegalStateException("No JWT signing key configured (jugueria.identity.jwt.private-key)");
				}
				log.warn("No JWT signing key configured: tokens are signed with a key that lives only in this process");
				return ephemeral(keyId);
			}
			var privateKey = RsaKeyConverters.pkcs8()
				.convert(new ByteArrayInputStream(privateKeyPem.getBytes(StandardCharsets.UTF_8)));
			return new RSAKey.Builder(publicKeyOf(privateKey)).privateKey(privateKey).keyID(keyId).build();
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Cannot build the JWT signing key", e);
		}
	}

	private static RSAKey ephemeral(String keyId) throws GeneralSecurityException {
		var generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		var pair = generator.generateKeyPair();
		return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey(pair.getPrivate()).keyID(keyId).build();
	}

	private static RSAPublicKey publicKeyOf(RSAPrivateKey privateKey) throws GeneralSecurityException {
		if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
			throw new IllegalStateException("The JWT signing key must be a PKCS#8 RSA private key");
		}
		var spec = new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent());
		return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
	}

}
