package com.jhanantezana.jugueria.catalog.web;

import java.util.UUID;

import com.jhanantezana.jugueria.catalog.internal.Station;

record StationOptionResponse(UUID id, String name) {

	static StationOptionResponse from(Station station) {
		return new StationOptionResponse(station.getId(), station.getName());
	}

}
