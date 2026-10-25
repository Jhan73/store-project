package com.jhanantezana.jugueria.catalog.web;

import java.util.UUID;

import com.jhanantezana.jugueria.catalog.internal.Station;
import com.jhanantezana.jugueria.shared.ETags;

record StationResponse(UUID id, String name, boolean defaultStation, boolean active, String etag) {

	static StationResponse from(Station station) {
		return new StationResponse(station.getId(), station.getName(), station.isDefaultStation(), station.isActive(),
				ETags.format(station.getVersion()));
	}

}
