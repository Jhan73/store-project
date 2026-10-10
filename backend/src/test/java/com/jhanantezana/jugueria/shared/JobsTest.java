package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.List;

import org.junit.jupiter.api.Test;

class JobsTest {

	@Test
	void keepsClaimingWhileBatchesComeBackFull() {
		var batches = new ArrayDeque<>(List.of(Jobs.BATCH_SIZE, Jobs.BATCH_SIZE, 7));

		var total = Jobs.drain(batches::poll);

		assertThat(total).isEqualTo(2 * Jobs.BATCH_SIZE + 7);
		assertThat(batches).isEmpty();
	}

	@Test
	void stopsAfterTheFirstPartialBatch() {
		var calls = new int[1];

		var total = Jobs.drain(() -> {
			calls[0]++;
			return 0;
		});

		assertThat(total).isZero();
		assertThat(calls[0]).isEqualTo(1);
	}

	@Test
	void claimsAtMostOneHundredRowsPerBatch() {
		assertThat(Jobs.BATCH_SIZE).isEqualTo(100);
	}

}
