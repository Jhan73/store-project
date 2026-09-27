package com.jhanantezana.jugueria.store.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.internal.ReasonService;

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
	ResponseEntity<ReasonResponse> create(@Valid @RequestBody CreateReasonRequest request) {
		var reason = reasons.create(request.type(), request.code());
		return ResponseEntity.created(URI.create("/api/v1/admin/reasons/" + reason.getId()))
			.body(ReasonResponse.from(reason));
	}

	@PostMapping("/{id}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	ReasonResponse deactivate(@PathVariable UUID id) {
		return ReasonResponse.from(reasons.deactivate(id));
	}

	@PostMapping("/{id}/reactivate")
	@PreAuthorize("hasRole('ADMIN')")
	ReasonResponse reactivate(@PathVariable UUID id) {
		return ReasonResponse.from(reasons.reactivate(id));
	}

}
