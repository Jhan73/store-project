package com.jhanantezana.jugueria.instore.web;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.internal.SaleVoidView;

record SaleVoidResponse(String reasonCode, @Nullable String note, UUID voidedBy, Instant voidedAt) {

	static SaleVoidResponse from(SaleVoidView saleVoid) {
		return new SaleVoidResponse(saleVoid.reasonCode(), saleVoid.note(), saleVoid.voidedBy(), saleVoid.voidedAt());
	}

}
