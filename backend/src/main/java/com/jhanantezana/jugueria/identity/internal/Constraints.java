package com.jhanantezana.jugueria.identity.internal;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

final class Constraints {

	static final String USERNAME = "user_account_username_active_key";

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
