package com.jhanantezana.jugueria.shared;

import org.jspecify.annotations.Nullable;

public final class IfMatchHeader {

	private IfMatchHeader() {
	}

	public static long require(@Nullable String ifMatch) {
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
