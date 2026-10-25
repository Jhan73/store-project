package com.jhanantezana.jugueria.store.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.IfMatchHeader;
import com.jhanantezana.jugueria.store.internal.DeliveryZone;
import com.jhanantezana.jugueria.store.internal.DeliveryZoneService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
		var all = deliveryZones.list();
		var deletable = deliveryZones.deletable(all.stream().map(DeliveryZone::getId).toList());
		return all.stream().map(zone -> DeliveryZoneResponse.from(zone, deletable.contains(zone.getId()))).toList();
	}

	private DeliveryZoneResponse response(DeliveryZone zone) {
		return DeliveryZoneResponse.from(zone, !deliveryZones.deletable(List.of(zone.getId())).isEmpty());
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "204", description = "No Content")
	@ApiErrors({ "store.delivery-zone-not-found", "store.delivery-zone-in-use" })
	ResponseEntity<Void> delete(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		deliveryZones.delete(id, IfMatchHeader.require(ifMatch));
		return ResponseEntity.noContent().build();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "store.invalid-delivery-zone", "store.delivery-zone-name-already-used", "store.delivery-zone-currency-mismatch" })
	ResponseEntity<DeliveryZoneResponse> create(@Valid @RequestBody CreateDeliveryZoneRequest request) {
		var zone = deliveryZones.create(request.name(), request.fee(), request.deliveryMinutes(),
				request.minimumOrder(), request.freeDeliveryThreshold());
		return ResponseEntity.created(URI.create("/api/v1/admin/delivery-zones/" + zone.getId()))
			.eTag(ETags.format(zone.getVersion()))
			.body(response(zone));
	}

	@PatchMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "store.delivery-zone-not-found", "store.invalid-delivery-zone", "store.delivery-zone-name-already-used", "store.delivery-zone-currency-mismatch" })
	ResponseEntity<DeliveryZoneResponse> change(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@Valid @RequestBody UpdateDeliveryZoneRequest request) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		var zone = deliveryZones.change(id, request.name(), request.fee(), request.deliveryMinutes(),
				request.minimumOrder(), request.freeDeliveryThreshold(), expectedVersion);
		return ResponseEntity.ok().eTag(ETags.format(zone.getVersion())).body(response(zone));
	}

	@PostMapping("/{id}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "store.delivery-zone-not-found" })
	ResponseEntity<DeliveryZoneResponse> deactivate(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		var zone = deliveryZones.deactivate(id, expectedVersion);
		return ResponseEntity.ok().eTag(ETags.format(zone.getVersion())).body(response(zone));
	}

	@PostMapping("/{id}/reactivate")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "store.delivery-zone-not-found" })
	ResponseEntity<DeliveryZoneResponse> reactivate(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		var zone = deliveryZones.reactivate(id, expectedVersion);
		return ResponseEntity.ok().eTag(ETags.format(zone.getVersion())).body(response(zone));
	}

}
