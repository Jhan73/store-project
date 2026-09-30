package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class IdsTest {

	@Test
	void generatesVersion7Ids() {
		assertThat(Ids.newId().version()).isEqualTo(7);
	}

	@Test
	void generatesIdsInCreationOrder() {
		var ids = Stream.generate(Ids::newId).limit(1_000).toList();

		assertThat(ids).isSortedAccordingTo(UUID::compareTo).doesNotHaveDuplicates();
	}

}
