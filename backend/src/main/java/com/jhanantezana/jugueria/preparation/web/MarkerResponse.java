package com.jhanantezana.jugueria.preparation.web;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.preparation.internal.MarkerKind;
import com.jhanantezana.jugueria.preparation.internal.MarkerView;

record MarkerResponse(UUID id, UUID boardOrderId, @Nullable UUID lineId, MarkerKind kind, Instant createdAt,
		@Nullable Instant acknowledgedAt) {

	static MarkerResponse from(MarkerView marker) {
		return new MarkerResponse(marker.id(), marker.boardOrderId(), marker.lineId(), marker.kind(),
				marker.createdAt(), marker.acknowledgedAt());
	}

}
