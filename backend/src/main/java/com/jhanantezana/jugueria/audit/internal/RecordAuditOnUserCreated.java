package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;

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
		var after = new LinkedHashMap<String, Object>();
		if (event.username() != null) {
			after.put("username", event.username());
		}
		after.put("role", event.role().name());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "USER_CREATED",
				ENTITY_TYPE, event.userId(), null, after, null, event.username()));
	}

}
