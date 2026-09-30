package com.jhanantezana.jugueria.catalog.internal;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

final class Constraints {

	static final String PRODUCT_NAME = "product_category_name_key";

	static final String PRODUCT_MODIFIER_GROUP_FK = "product_modifier_group_group_id_fkey";

	static final String MODIFIER_GROUP_NAME = "modifier_group_name_key";

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
