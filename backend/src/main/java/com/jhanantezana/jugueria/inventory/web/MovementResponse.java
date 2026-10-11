package com.jhanantezana.jugueria.inventory.web;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.inventory.internal.MovementView;
import com.jhanantezana.jugueria.inventory.internal.StockMovementType;

record MovementResponse(UUID id, UUID productId, StockMovementType type, int onHandDelta, int reservedDelta,
		@Nullable UUID referenceId, @Nullable String reasonCode, @Nullable String note, @Nullable UUID actorId,
		@Nullable String actorRole, Instant occurredAt) {

	static MovementResponse from(MovementView movement) {
		return new MovementResponse(movement.id(), movement.productId(), movement.type(), movement.onHandDelta(),
				movement.reservedDelta(), movement.referenceId(), movement.reasonCode(), movement.note(),
				movement.actorId(), movement.actorRole(), movement.occurredAt());
	}

}
