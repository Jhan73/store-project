package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IfMatchHeaderTest {

	@Test
	void parsesAQuotedVersion() {
		assertThat(IfMatchHeader.require("\"7\"")).isEqualTo(7);
	}

	@Test
	void parsesAWeakVersion() {
		assertThat(IfMatchHeader.require("W/\"3\"")).isEqualTo(3);
	}

	@Test
	void requiresTheHeader() {
		assertThatThrownBy(() -> IfMatchHeader.require(null)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(CommonError.PRECONDITION_REQUIRED));
		assertThatThrownBy(() -> IfMatchHeader.require(" ")).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(CommonError.PRECONDITION_REQUIRED));
	}

	@Test
	void rejectsAMalformedVersion() {
		assertThatThrownBy(() -> IfMatchHeader.require("\"abc\"")).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(CommonError.MALFORMED_REQUEST));
	}

}
