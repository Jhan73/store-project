package com.jhanantezana.jugueria.store.web;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.IfMatchHeader;
import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.internal.ReasonService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/reasons")
class ReasonController {

	private final ReasonService reasons;

	ReasonController(ReasonService reasons) {
		this.reasons = reasons;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	List<ReasonResponse> byType(@RequestParam ReasonType type) {
		return reasons.byType(type).stream().map(ReasonResponse::from).toList();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "store.reason-code-already-used" })
	ResponseEntity<ReasonResponse> create(@Valid @RequestBody CreateReasonRequest request) {
		var reason = reasons.create(request.type(), request.code());
		return ResponseEntity.created(URI.create("/api/v1/admin/reasons/" + reason.getId()))
			.eTag(ETags.format(reason.getVersion()))
			.body(ReasonResponse.from(reason));
	}

	@PostMapping("/{id}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "store.reason-not-found" })
	ResponseEntity<ReasonResponse> deactivate(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		var reason = reasons.deactivate(id, expectedVersion);
		return ResponseEntity.ok().eTag(ETags.format(reason.getVersion())).body(ReasonResponse.from(reason));
	}

	@PostMapping("/{id}/reactivate")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "store.reason-not-found" })
	ResponseEntity<ReasonResponse> reactivate(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		var reason = reasons.reactivate(id, expectedVersion);
		return ResponseEntity.ok().eTag(ETags.format(reason.getVersion())).body(ReasonResponse.from(reason));
	}

}
