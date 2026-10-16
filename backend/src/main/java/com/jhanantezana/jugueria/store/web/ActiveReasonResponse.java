package com.jhanantezana.jugueria.store.web;

import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.internal.Reason;

record ActiveReasonResponse(ReasonType type, String code) {

	static ActiveReasonResponse from(Reason reason) {
		return new ActiveReasonResponse(reason.getType(), reason.getCode());
	}

}
