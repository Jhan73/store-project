package com.jhanantezana.jugueria.store.web;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.ETags;

final class IfMatchHeader {

	private IfMatchHeader() {
	}

	static long require(@Nullable String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank()) {
			throw new BusinessException(CommonError.PRECONDITION_REQUIRED, "If-Match header is required for this update");
		}
		try {
			return ETags.parse(ifMatch);
		}
		catch (IllegalArgumentException e) {
			throw new BusinessException(CommonError.MALFORMED_REQUEST, "If-Match header is malformed");
		}
	}

}
