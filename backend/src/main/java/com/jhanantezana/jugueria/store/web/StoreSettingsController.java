package com.jhanantezana.jugueria.store.web;

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

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.store.internal.StoreSettingsService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/settings")
class StoreSettingsController {

	private final StoreSettingsService settingsService;

	StoreSettingsController(StoreSettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<StoreSettingsResponse> get() {
		var settings = settingsService.get();
		return ResponseEntity.ok().eTag(ETags.format(settings.getVersion())).body(StoreSettingsResponse.from(settings));
	}

	@PutMapping
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<StoreSettingsResponse> update(
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@Valid @RequestBody UpdateStoreSettingsRequest request) {
		var expectedVersion = requireIfMatch(ifMatch);
		var settings = settingsService.update(request.timeZone(), request.currency(), request.basePrepMinutes(),
				request.queueMinutesPerOrder(), request.busyModeMinutes(), request.boardWarningMinutes(),
				request.boardLateMinutes(), request.registerDifferenceThreshold(), request.exceptionThreshold(),
				request.onlineCapacityLimit(), expectedVersion);
		return ResponseEntity.ok().eTag(ETags.format(settings.getVersion())).body(StoreSettingsResponse.from(settings));
	}

	private static long requireIfMatch(@Nullable String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank()) {
			throw new BusinessException(CommonError.PRECONDITION_REQUIRED,
					"If-Match header is required to update settings");
		}
		try {
			return ETags.parse(ifMatch);
		}
		catch (IllegalArgumentException e) {
			throw new BusinessException(CommonError.MALFORMED_REQUEST, "If-Match header is malformed");
		}
	}

}
