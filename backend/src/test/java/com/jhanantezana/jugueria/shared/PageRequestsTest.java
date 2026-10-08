package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

class PageRequestsTest {

	@Test
	void raisesANegativePageToZero() {
		assertThat(PageRequests.of(-1, 20).getPageNumber()).isZero();
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -5, Integer.MIN_VALUE })
	void raisesANonPositiveSizeToOne(int size) {
		assertThat(PageRequests.of(0, size).getPageSize()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(ints = { 101, 1000, Integer.MAX_VALUE })
	void capsTheSizeAtOneHundred(int size) {
		assertThat(PageRequests.of(0, size).getPageSize()).isEqualTo(100);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 20, 100 })
	void keepsTheLargestPageWhoseOffsetFitsAnInt(int size) {
		var largest = Integer.MAX_VALUE / size;

		var request = PageRequests.of(largest, size);

		assertThat(request.getPageNumber()).isEqualTo(largest);
		assertThat(request.getOffset()).isLessThanOrEqualTo(Integer.MAX_VALUE);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 20, 100 })
	void pullsAPageWhoseOffsetOverflowsAnIntBackToTheLargestOne(int size) {
		var request = PageRequests.of(Integer.MAX_VALUE, size, Sort.by("id"));

		assertThat(request.getPageNumber()).isEqualTo(Integer.MAX_VALUE / size);
		assertThat(request.getOffset()).isLessThanOrEqualTo(Integer.MAX_VALUE);
		assertThat(request.getSort()).isEqualTo(Sort.by("id"));
	}

}
