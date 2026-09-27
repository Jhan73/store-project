package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ETagsTest {

	@Test
	void formatsAVersionAsAQuotedString() {
		assertThat(ETags.format(3)).isEqualTo("\"3\"");
	}

	@Test
	void parsesAQuotedETag() {
		assertThat(ETags.parse("\"3\"")).isEqualTo(3L);
	}

	@Test
	void parsesABareNumber() {
		assertThat(ETags.parse("3")).isEqualTo(3L);
	}

	@Test
	void rejectsAMalformedETag() {
		assertThatThrownBy(() -> ETags.parse("not-a-number")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void parsesAWeakETag() {
		assertThat(ETags.parse("W/\"3\"")).isEqualTo(3L);
	}

}
