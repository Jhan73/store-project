package com.jhanantezana.jugueria.instore.internal;

import java.util.Map;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.ETags;

final class EntityVersions {

	private EntityVersions() {
	}

	// Short-circuits the common case; Hibernate's own version-guarded UPDATE at flush is the real race guard.
	static void requireMatching(long currentVersion, long expectedVersion) {
		if (currentVersion != expectedVersion) {
			throw new BusinessException(CommonError.PRECONDITION_FAILED, "If-Match does not match the current ETag",
					Map.of("currentETag", ETags.format(currentVersion)));
		}
	}

}
