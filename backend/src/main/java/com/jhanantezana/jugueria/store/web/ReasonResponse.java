package com.jhanantezana.jugueria.store.web;

import java.util.UUID;

import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.internal.Reason;

record ReasonResponse(UUID id, ReasonType type, String code, boolean active) {

	static ReasonResponse from(Reason reason) {
		return new ReasonResponse(reason.getId(), reason.getType(), reason.getCode(), reason.isActive());
	}

}
