package com.jhanantezana.jugueria.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class RequestHash {

	// Sorted keys: the same request must hash the same however the client ordered its JSON.
	private static final JsonMapper CANONICAL = JsonMapper.builder()
		.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
		.build();

	private RequestHash() {
	}

	/** SHA-256 (hex) of method, path and the canonical JSON of the body. */
	public static String of(String method, String path, @Nullable Object body) {
		var canonical = method + "\n" + path + "\n" + CANONICAL.writeValueAsString(body);
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by the JDK", e);
		}
	}

}
