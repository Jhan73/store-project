package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class RecipientAllowlistTest {

	@Test
	void allowsEveryoneWhenEmpty() {
		var allowlist = new RecipientAllowlist(List.of());

		assertThat(allowlist.allows("anyone@example.com")).isTrue();
	}

	@Test
	void allowsAnExactMatchCaseInsensitively() {
		var allowlist = new RecipientAllowlist(List.of("Owner@Jugueria.pe"));

		assertThat(allowlist.allows("owner@jugueria.pe")).isTrue();
	}

	@Test
	void allowsAnyLocalPartOfAnAllowedDomain() {
		var allowlist = new RecipientAllowlist(List.of("@jugueria.pe"));

		assertThat(allowlist.allows("new-staff@jugueria.pe")).isTrue();
	}

	@Test
	void rejectsAnAddressOutsideTheAllowlist() {
		var allowlist = new RecipientAllowlist(List.of("owner@jugueria.pe"));

		assertThat(allowlist.allows("someone-else@example.com")).isFalse();
	}

}
