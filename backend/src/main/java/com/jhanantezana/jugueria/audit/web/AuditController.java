package com.jhanantezana.jugueria.audit.web;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.audit.internal.AuditLogService;
import com.jhanantezana.jugueria.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/audit-entries")
class AuditController {

	private static final int MAX_PAGE_SIZE = 100;

	private final AuditLogService auditLog;

	AuditController(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	// Sort is always occurredAt desc, which is also the partition key: no client-chosen sort field.
	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	PageResponse<AuditEntryResponse> search(@RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) String entityType, @RequestParam(required = false) UUID entityId,
			@RequestParam(required = false) String action, @RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		var bounded = Math.min(size, MAX_PAGE_SIZE);
		var pageable = PageRequest.of(page, bounded, Sort.by("occurredAt").descending());
		return PageResponse.from(auditLog.search(actorId, entityType, entityId, action, from, to, pageable),
				AuditEntryResponse::from);
	}

}
