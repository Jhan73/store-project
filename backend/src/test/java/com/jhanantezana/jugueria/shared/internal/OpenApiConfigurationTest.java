package com.jhanantezana.jugueria.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.jhanantezana.jugueria.shared.ErrorCode;

class OpenApiConfigurationTest {

	record Code(String code, HttpStatus status) implements ErrorCode {
	}

	@Test
	void indexesCodesByTheirWireValue() {
		var index = OpenApiConfiguration.index(Stream.of(new Code("a.one", HttpStatus.CONFLICT),
				new Code("a.two", HttpStatus.NOT_FOUND)));

		assertThat(index).containsOnlyKeys("a.one", "a.two");
	}

	@Test
	void rejectsTheSameWireCodeDeclaredTwice() {
		var duplicated = Stream.<ErrorCode>of(new Code("a.one", HttpStatus.CONFLICT), new Code("a.one", HttpStatus.NOT_FOUND));

		assertThatIllegalStateException().isThrownBy(() -> OpenApiConfiguration.index(duplicated))
			.withMessageContaining("a.one");
	}

}
