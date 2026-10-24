package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** Two open tickets became one: the source's lines now belong to the target and the source's table is free. */
public record TicketsMerged(UUID targetTicketId, UUID sourceTicketId, long sourceNumber, @Nullable UUID sourceTableId,
		List<UUID> movedLineIds, @Nullable UUID actorId, @Nullable Role actorRole, Instant occurredAt) {

	public TicketsMerged {
		movedLineIds = List.copyOf(movedLineIds);
	}

}
