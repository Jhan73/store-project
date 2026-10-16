package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class IdempotencyKeyHeaderTest {

	@Test
	void parsesAUuid() {
		var key = UUID.randomUUID();

		assertThat(IdempotencyKeyHeader.require(key.toString())).isEqualTo(key);
	}

	@Test
	void requiresTheHeader() {
		assertThatThrownBy(() -> IdempotencyKeyHeader.require(null)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(CommonError.IDEMPOTENCY_KEY_REQUIRED));
		assertThatThrownBy(() -> IdempotencyKeyHeader.require(" ")).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(CommonError.IDEMPOTENCY_KEY_REQUIRED));
	}

	@Test
	void rejectsAValueThatIsNotAUuid() {
		assertThatThrownBy(() -> IdempotencyKeyHeader.require("not-a-uuid")).isInstanceOfSatisfying(
				BusinessException.class, e -> assertThat(e.errorCode()).isEqualTo(CommonError.MALFORMED_REQUEST));
	}

}
