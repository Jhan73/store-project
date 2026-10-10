package com.jhanantezana.jugueria.shared;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

public final class IdempotencyKeyHeader {

	private IdempotencyKeyHeader() {
	}

	public static UUID require(@Nullable String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new BusinessException(CommonError.IDEMPOTENCY_KEY_REQUIRED,
					"Idempotency-Key header is required for this command");
		}
		try {
			return UUID.fromString(idempotencyKey.trim());
		}
		catch (IllegalArgumentException e) {
			throw new BusinessException(CommonError.MALFORMED_REQUEST, "Idempotency-Key header must be a UUID");
		}
	}

}
