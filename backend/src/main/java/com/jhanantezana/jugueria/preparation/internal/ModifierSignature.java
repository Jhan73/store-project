package com.jhanantezana.jugueria.preparation.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Tells identical lines apart from look-alikes: the same options, in any order, give the same signature. */
final class ModifierSignature {

	private ModifierSignature() {
	}

	static String of(List<UUID> optionIds) {
		var sorted = optionIds.stream().map(UUID::toString).sorted().toList();
		return sha256(String.join(",", sorted));
	}

	private static String sha256(String text) {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is always available", e);
		}
	}

}
