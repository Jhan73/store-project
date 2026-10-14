package com.jhanantezana.jugueria.store.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.internal.ReasonService;

// What floor staff may pick from; managing the lists stays in the admin controller. A server only voids lines.
@RestController
@RequestMapping("/api/v1/reasons")
class ActiveReasonController {

	private final ReasonService reasons;

	ActiveReasonController(ReasonService reasons) {
		this.reasons = reasons;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('CASHIER', 'ADMIN') or (hasRole('SERVER') and #type.name() == 'VOID')")
	List<ActiveReasonResponse> active(@RequestParam ReasonType type) {
		return reasons.activeByType(type).stream().map(ActiveReasonResponse::from).toList();
	}

}
