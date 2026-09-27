package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class SingletonsTest {

	@Test
	void returnsTheOnlyRow() {
		assertThat(Singletons.requireOne(List.of("x"))).isEqualTo("x");
	}

	@Test
	void rejectsAnEmptyList() {
		assertThatThrownBy(() -> Singletons.requireOne(List.of())).isInstanceOf(IllegalStateException.class);
	}

}
