package com.jhanantezana.jugueria.instore.web;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.instore.internal.DiningTable;
import com.jhanantezana.jugueria.shared.ETags;

record TableResponse(UUID id, String name, @Nullable String area, int displayOrder, boolean active,
		boolean deletable, String etag) {

	static TableResponse from(DiningTable table, boolean deletable) {
		return new TableResponse(table.getId(), table.getName(), table.getArea(), table.getDisplayOrder(),
				table.isActive(), deletable, ETags.format(table.getVersion()));
	}

}
