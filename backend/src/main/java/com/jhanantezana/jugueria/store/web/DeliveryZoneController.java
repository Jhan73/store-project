package com.jhanantezana.jugueria.store.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.store.internal.DeliveryZoneService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/delivery-zones")
class DeliveryZoneController {

	private final DeliveryZoneService deliveryZones;

	DeliveryZoneController(DeliveryZoneService deliveryZones) {
		this.deliveryZones = deliveryZones;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	List<DeliveryZoneResponse> list() {
		return deliveryZones.list().stream().map(DeliveryZoneResponse::from).toList();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<DeliveryZoneResponse> create(@Valid @RequestBody CreateDeliveryZoneRequest request) {
		var zone = deliveryZones.create(request.name(), request.fee(), request.deliveryMinutes(),
				request.minimumOrder(), request.freeDeliveryThreshold());
		return ResponseEntity.created(URI.create("/api/v1/admin/delivery-zones/" + zone.getId()))
			.body(DeliveryZoneResponse.from(zone));
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	DeliveryZoneResponse change(@PathVariable UUID id, @Valid @RequestBody UpdateDeliveryZoneRequest request) {
		var zone = deliveryZones.change(id, request.name(), request.fee(), request.deliveryMinutes(),
				request.minimumOrder(), request.freeDeliveryThreshold());
		return DeliveryZoneResponse.from(zone);
	}

	@PostMapping("/{id}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	DeliveryZoneResponse deactivate(@PathVariable UUID id) {
		return DeliveryZoneResponse.from(deliveryZones.deactivate(id));
	}

	@PostMapping("/{id}/reactivate")
	@PreAuthorize("hasRole('ADMIN')")
	DeliveryZoneResponse reactivate(@PathVariable UUID id) {
		return DeliveryZoneResponse.from(deliveryZones.reactivate(id));
	}

}
