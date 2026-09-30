package com.jhanantezana.jugueria.instore.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ConstraintsTest {

	@Test
	void recognisesTheTableNameIndexBehindAViolation() {
		var cause = new ConstraintViolationException("dup", new SQLException("dup"), Constraints.TABLE_NAME);

		assertThat(Constraints.violated(new DataIntegrityViolationException("x", cause), Constraints.TABLE_NAME))
			.isTrue();
	}

	@Test
	void doesNotMistakeAnotherConstraintForIt() {
		var cause = new ConstraintViolationException("check", new SQLException("check"),
				"dining_table_display_order_check");

		assertThat(Constraints.violated(new DataIntegrityViolationException("x", cause), Constraints.TABLE_NAME))
			.isFalse();
	}

	@Test
	void anUnrelatedFailureMatchesNothing() {
		assertThat(Constraints.violated(new DataIntegrityViolationException("x"), Constraints.TABLE_NAME)).isFalse();
	}

}
