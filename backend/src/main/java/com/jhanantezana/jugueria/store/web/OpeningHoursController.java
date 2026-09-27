package com.jhanantezana.jugueria.store.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
	List<OpeningHourResponse> list() {
		return openingHours.list().stream().map(OpeningHourResponse::from).toList();
	}

	@PutMapping
	@PreAuthorize("hasRole('ADMIN')")
	List<OpeningHourResponse> replaceAll(@Valid @RequestBody List<UpdateOpeningHourRequest> requests) {
		var updates = requests.stream().map(UpdateOpeningHourRequest::toUpdate).toList();
		return openingHours.replaceAll(updates).stream().map(OpeningHourResponse::from).toList();
	}

}
