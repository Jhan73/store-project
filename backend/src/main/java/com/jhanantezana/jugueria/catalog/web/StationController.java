package com.jhanantezana.jugueria.catalog.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.catalog.internal.StationService;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.IfMatchHeader;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/stations")
class StationController {

	private final StationService stations;

	StationController(StationService stations) {
		this.stations = stations;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	List<StationResponse> list() {
		return stations.list().stream().map(StationResponse::from).toList();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "catalog.station-name-already-used" })
	ResponseEntity<StationResponse> create(@Valid @RequestBody SaveStationRequest request) {
		var station = stations.create(request.name());
		return ResponseEntity.created(URI.create("/api/v1/admin/stations/" + station.getId()))
			.eTag(ETags.format(station.getVersion()))
			.body(StationResponse.from(station));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "catalog.station-not-found", "catalog.station-name-already-used" })
	ResponseEntity<StationResponse> rename(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@Valid @RequestBody SaveStationRequest request) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		var station = stations.rename(id, request.name(), expectedVersion);
		return ResponseEntity.ok().eTag(ETags.format(station.getVersion())).body(StationResponse.from(station));
	}

}
