package com.jhanantezana.jugueria.store.web;

import java.util.UUID;

import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.internal.Reason;

record ReasonResponse(UUID id, ReasonType type, String code, boolean active, String etag) {

	static ReasonResponse from(Reason reason) {
		return new ReasonResponse(reason.getId(), reason.getType(), reason.getCode(), reason.isActive(),
				ETags.format(reason.getVersion()));
	}

}
