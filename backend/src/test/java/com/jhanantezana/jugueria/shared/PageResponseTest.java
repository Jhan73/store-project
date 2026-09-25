package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

	@Test
	void copiesThePageMetadataAndMapsTheContent() {
		Page<Integer> page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 5);

		var response = PageResponse.from(page, String::valueOf);

		assertThat(response).isEqualTo(new PageResponse<>(List.of("1", "2"), 1, 2, 5, 3));
	}

	@Test
	void neverHoldsANullContent() {
		assertThat(new PageResponse<>(null, 0, 20, 0, 0).content()).isEmpty();
	}

}
