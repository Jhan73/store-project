package com.jhanantezana.jugueria.audit.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.jhanantezana.jugueria.shared.CorrelationId;
import com.jhanantezana.jugueria.shared.Role;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuditLogService {

	// Reserved: never assignable to a real user account, only written here for system-initiated changes.
	static final String SYSTEM_ACTOR_ROLE = "SYSTEM";

	private final AuditLogRepository auditLogs;

	AuditLogService(AuditLogRepository auditLogs) {
		this.auditLogs = auditLogs;
	}

	// Runs in the caller's transaction (default propagation): a failure here rolls back the business change.
	@Transactional
	void record(AuditEntry entry) {
		var request = currentRequest();
		auditLogs.save(new AuditLog(entry.occurredAt(), entry.actorId(), resolveActorRole(entry.actorRole()),
				entry.action(), entry.entityType(), entry.entityId(), entry.before(), entry.after(), entry.reason(),
				CorrelationId.current().orElse(null), request.map(HttpServletRequest::getRemoteAddr).orElse(null),
				request.map(r -> r.getHeader("User-Agent")).orElse(null)));
	}

	@Transactional(readOnly = true)
	public Page<AuditLog> search(@Nullable UUID actorId, @Nullable String entityType, @Nullable UUID entityId,
			@Nullable String action, @Nullable Instant from, @Nullable Instant to, Pageable pageable) {
		var filter = new AuditSearchFilter(actorId, entityType, entityId, action, from, to);
		return auditLogs.findAll(AuditLogSpecifications.matching(filter), pageable);
	}

	static String resolveActorRole(@Nullable Role actorRole) {
		return actorRole == null ? SYSTEM_ACTOR_ROLE : actorRole.name();
	}

	private static Optional<HttpServletRequest> currentRequest() {
		var attributes = RequestContextHolder.getRequestAttributes();
		return attributes instanceof ServletRequestAttributes servletAttributes
				? Optional.of(servletAttributes.getRequest()) : Optional.empty();
	}

}
