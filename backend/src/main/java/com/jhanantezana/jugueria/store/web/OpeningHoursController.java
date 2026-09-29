package com.jhanantezana.jugueria.store.web;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.IfMatchHeader;
import com.jhanantezana.jugueria.store.internal.OpeningHoursService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/settings/opening-hours")
class OpeningHoursController {

	private final OpeningHoursService openingHours;

	OpeningHoursController(OpeningHoursService openingHours) {
		this.openingHours = openingHours;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<List<OpeningHourResponse>> list() {
		var result = openingHours.list();
		return ResponseEntity.ok()
			.eTag(ETags.format(result.version()))
			.body(result.hours().stream().map(OpeningHourResponse::from).toList());
	}

	@PutMapping
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<List<OpeningHourResponse>> replaceAll(
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@Valid @RequestBody List<UpdateOpeningHourRequest> requests) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		var updates = requests.stream().map(UpdateOpeningHourRequest::toUpdate).toList();
		var result = openingHours.replaceAll(updates, expectedVersion);
		return ResponseEntity.ok()
			.eTag(ETags.format(result.version()))
			.body(result.hours().stream().map(OpeningHourResponse::from).toList());
	}

}
