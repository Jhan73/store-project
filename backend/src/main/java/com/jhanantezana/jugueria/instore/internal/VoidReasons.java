package com.jhanantezana.jugueria.instore.internal;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.InstoreError;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.StoreApi;

/** Checks the reason a void gives against the store's lists; the reason "Other" needs a note. */
@Component
class VoidReasons {

	private static final String OTHER_REASON = "other";

	private final StoreApi store;

	VoidReasons(StoreApi store) {
		this.store = store;
	}

	/** @return the code as the store spells it */
	String require(ReasonType type, String reasonCode, @Nullable String note) {
		var reason = store.reasons(type)
			.stream()
			.filter(candidate -> candidate.active() && candidate.code().equalsIgnoreCase(reasonCode.trim()))
			.findFirst()
			.orElseThrow(() -> new BusinessException(InstoreError.INVALID_REASON,
					"The reason is not an active " + type.name().toLowerCase().replace('_', '-') + " reason"));
		if (OTHER_REASON.equalsIgnoreCase(reason.code()) && note == null) {
			throw new BusinessException(InstoreError.INVALID_REASON, "The reason \"Other\" needs a note");
		}
		return reason.code();
	}

}
