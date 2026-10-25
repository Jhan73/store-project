package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.UserDeleted;

// Plain synchronous listener: an audit failure must roll back the deletion.
@Component
class RecordAuditOnUserDeleted {

	private static final String ENTITY_TYPE = "USER";

	private final AuditLogService auditLog;

	RecordAuditOnUserDeleted(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(UserDeleted event) {
		var snapshot = new LinkedHashMap<String, Object>();
		if (event.username() != null) {
			snapshot.put("username", event.username());
		}
		snapshot.put("role", event.role().name());
		snapshot.put("active", event.active());
		auditLog.record(AuditEntry.deletion(event.occurredAt(), event.actorId(), event.actorRole(), ENTITY_TYPE,
				event.userId(), snapshot, event.username(), null));
	}

}
