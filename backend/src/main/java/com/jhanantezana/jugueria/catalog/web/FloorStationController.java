package com.jhanantezana.jugueria.catalog.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.catalog.internal.StationService;

// The stations as floor staff pick them (the board's station filter); managing them stays in the admin controller.
@RestController
@RequestMapping("/api/v1/stations")
class FloorStationController {

	private final StationService stations;

	FloorStationController(StationService stations) {
		this.stations = stations;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('SERVER', 'CASHIER', 'ADMIN')")
	List<StationOptionResponse> list() {
		return stations.listActive().stream().map(StationOptionResponse::from).toList();
	}

}
