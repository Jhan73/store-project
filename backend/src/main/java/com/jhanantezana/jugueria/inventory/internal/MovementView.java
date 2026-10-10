package com.jhanantezana.jugueria.inventory.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record MovementView(UUID id, UUID productId, StockMovementType type, int onHandDelta, int reservedDelta,
		@Nullable UUID referenceId, @Nullable String reasonCode, @Nullable String note, @Nullable UUID actorId,
		@Nullable String actorRole, Instant occurredAt) {

	static MovementView from(ResultSet rs) throws SQLException {
		return new MovementView(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
				StockMovementType.valueOf(rs.getString("type")), rs.getInt("on_hand_delta"),
				rs.getInt("reserved_delta"), rs.getObject("reference_id", UUID.class), rs.getString("reason_code"),
				rs.getString("note"), rs.getObject("actor_id", UUID.class), rs.getString("actor_role"),
				rs.getObject("occurred_at", OffsetDateTime.class).toInstant());
	}

}
