package com.jhanantezana.jugueria.audit.internal;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.UserDeactivated;

@Component
class RecordAuditOnUserDeactivated {

	private static final String ENTITY_TYPE = "USER";

	private final AuditLogService auditLog;

	RecordAuditOnUserDeactivated(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(UserDeactivated event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "USER_DEACTIVATED",
				ENTITY_TYPE, event.userId(), Map.of("active", true), Map.of("active", false), null));
	}

}
