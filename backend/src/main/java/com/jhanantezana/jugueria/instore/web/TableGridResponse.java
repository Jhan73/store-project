package com.jhanantezana.jugueria.instore.web;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.TableStatus;
import com.jhanantezana.jugueria.instore.internal.TableGridEntry;

record TableGridResponse(UUID id, String name, @Nullable String area, int displayOrder, TableStatus status,
		@Nullable Instant ticketOpenedAt) {

	static TableGridResponse from(TableGridEntry entry) {
		var table = entry.table();
		return new TableGridResponse(table.getId(), table.getName(), table.getArea(), table.getDisplayOrder(),
				entry.status(), entry.ticketOpenedAt());
	}

}
