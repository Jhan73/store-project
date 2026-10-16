package com.jhanantezana.jugueria.store.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.store.internal.StoreSettingsService;

// Only what the board needs (age thresholds and the store zone for its clock times); the rest of the settings stay in the admin controller.
@RestController
@RequestMapping("/api/v1/store/board-thresholds")
class BoardThresholdsController {

	private final StoreSettingsService settingsService;

	BoardThresholdsController(StoreSettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('SERVER', 'CASHIER', 'ADMIN')")
	BoardThresholdsResponse get() {
		return BoardThresholdsResponse.from(settingsService.get());
	}

}
