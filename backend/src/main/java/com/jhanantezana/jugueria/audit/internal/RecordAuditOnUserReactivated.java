package com.jhanantezana.jugueria.audit.internal;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.UserReactivated;

@Component
class RecordAuditOnUserReactivated {

	private static final String ENTITY_TYPE = "USER";

	private final AuditLogService auditLog;

	RecordAuditOnUserReactivated(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(UserReactivated event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "USER_REACTIVATED",
				ENTITY_TYPE, event.userId(), Map.of("active", false), Map.of("active", true), null));
	}

}
