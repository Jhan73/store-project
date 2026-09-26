package com.jhanantezana.jugueria.audit.internal;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.UserRoleChanged;

@Component
class RecordAuditOnUserRoleChanged {

	private static final String ENTITY_TYPE = "USER";

	private final AuditLogService auditLog;

	RecordAuditOnUserRoleChanged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(UserRoleChanged event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "USER_ROLE_CHANGED",
				ENTITY_TYPE, event.userId(), Map.of("role", event.oldRole().name()),
				Map.of("role", event.newRole().name()), null));
	}

}
