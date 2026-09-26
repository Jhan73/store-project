package com.jhanantezana.jugueria.audit.internal;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.UserCreated;

// Plain synchronous listener (not @ApplicationModuleListener): an audit failure must roll back the
// business change, so this runs inside the publisher's own transaction, not after commit.
@Component
class RecordAuditOnUserCreated {

	private static final String ENTITY_TYPE = "USER";

	private final AuditLogService auditLog;

	RecordAuditOnUserCreated(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(UserCreated event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "USER_CREATED",
				ENTITY_TYPE, event.userId(), null, Map.of("email", event.email(), "role", event.role().name()),
				null));
	}

}
