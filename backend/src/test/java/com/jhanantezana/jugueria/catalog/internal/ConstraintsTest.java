package com.jhanantezana.jugueria.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ConstraintsTest {

	@Test
	void recognisesTheNamedConstraintBehindAViolation() {
		var cause = new ConstraintViolationException("dup", new SQLException("dup"), "product_category_name_key");

		assertThat(Constraints.violated(new DataIntegrityViolationException("x", cause), "product_category_name_key"))
			.isTrue();
	}

	@Test
	void doesNotMistakeAnotherConstraintForIt() {
		var cause = new ConstraintViolationException("fk", new SQLException("fk"),
				"product_modifier_group_group_id_fkey");

		assertThat(Constraints.violated(new DataIntegrityViolationException("x", cause), "product_category_name_key"))
			.isFalse();
	}

	@Test
	void anUnrelatedFailureMatchesNothing() {
		assertThat(Constraints.violated(new DataIntegrityViolationException("x"), "product_category_name_key"))
			.isFalse();
	}

}
