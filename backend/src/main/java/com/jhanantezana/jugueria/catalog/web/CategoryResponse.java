package com.jhanantezana.jugueria.catalog.web;

import java.util.UUID;

import com.jhanantezana.jugueria.catalog.internal.Category;
import com.jhanantezana.jugueria.shared.ETags;

record CategoryResponse(UUID id, String name, UUID stationId, int displayOrder, boolean active, String etag) {

	static CategoryResponse from(Category category) {
		return new CategoryResponse(category.getId(), category.getName(), category.getStationId(),
				category.getDisplayOrder(), category.isActive(), ETags.format(category.getVersion()));
	}

}
