package com.jhanantezana.jugueria.catalog.web;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.catalog.internal.AvailabilityService;

@RestController
@RequestMapping("/api/v1/catalog")
class AvailabilityController {

	private final AvailabilityService availability;

	AvailabilityController(AvailabilityService availability) {
		this.availability = availability;
	}

	@PutMapping("/products/{id}/availability")
	@PreAuthorize("hasAnyRole('SERVER', 'CASHIER', 'ADMIN')")
	AvailabilityResponse setProductAvailability(@PathVariable UUID id, @RequestBody AvailabilityRequest request) {
		availability.setProductAvailability(id, request.available());
		return new AvailabilityResponse(id, request.available());
	}

	@PutMapping("/modifier-options/{id}/availability")
	@PreAuthorize("hasAnyRole('SERVER', 'CASHIER', 'ADMIN')")
	AvailabilityResponse setOptionAvailability(@PathVariable UUID id, @RequestBody AvailabilityRequest request) {
		availability.setOptionAvailability(id, request.available());
		return new AvailabilityResponse(id, request.available());
	}

}
