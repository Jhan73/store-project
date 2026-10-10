package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RequestHashTest {

	record Body(String name, int quantity) {
	}

	@Test
	void isAStableSha256Hex() {
		var hash = RequestHash.of("POST", "/api/v1/tickets", new Body("mango", 2));

		assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
		assertThat(RequestHash.of("POST", "/api/v1/tickets", new Body("mango", 2))).isEqualTo(hash);
	}

	@Test
	void changesWithMethodPathOrBody() {
		var base = RequestHash.of("POST", "/api/v1/tickets", new Body("mango", 2));

		assertThat(RequestHash.of("PUT", "/api/v1/tickets", new Body("mango", 2))).isNotEqualTo(base);
		assertThat(RequestHash.of("POST", "/api/v1/orders", new Body("mango", 2))).isNotEqualTo(base);
		assertThat(RequestHash.of("POST", "/api/v1/tickets", new Body("mango", 3))).isNotEqualTo(base);
	}

	@Test
	void ignoresTheOrderOfMapEntries() {
		var first = new LinkedHashMap<String, Object>();
		first.put("a", 1);
		first.put("b", 2);
		var second = new LinkedHashMap<String, Object>();
		second.put("b", 2);
		second.put("a", 1);

		assertThat(RequestHash.of("POST", "/x", first)).isEqualTo(RequestHash.of("POST", "/x", second));
	}

	@Test
	void doesNotConfuseAnAbsentBodyWithAnEmptyOne() {
		assertThat(RequestHash.of("POST", "/x", null)).isNotEqualTo(RequestHash.of("POST", "/x", Map.of()));
	}

	@Test
	void doesNotLetThePathAbsorbTheBody() {
		assertThat(RequestHash.of("POST", "/a", Map.of("b", 1)))
			.isNotEqualTo(RequestHash.of("POST", "/a\n{\"b\":1}", null));
	}

}
