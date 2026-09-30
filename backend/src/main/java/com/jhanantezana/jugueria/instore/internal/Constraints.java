package com.jhanantezana.jugueria.instore.internal;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

final class Constraints {

	static final String TABLE_NAME = "dining_table_name_key";

	private Constraints() {
	}

	// A violation is only the caller's fault in the way its name says; any other one must not be relabelled.
	static boolean violated(DataIntegrityViolationException e, String constraintName) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& constraintName.equals(violation.getConstraintName())) {
				return true;
			}
		}
		return false;
	}

}
