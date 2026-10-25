package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.UserDeactivated;

@Component
class RecordAuditOnUserDeactivated {

	private static final String ENTITY_TYPE = "USER";

	private final AuditLogService auditLog;

	private final AuditLabels labels;

	RecordAuditOnUserDeactivated(AuditLogService auditLog, AuditLabels labels) {
		this.auditLog = auditLog;
		this.labels = labels;
	}

	@EventListener
	void on(UserDeactivated event) {
		var username = labels.user(event.userId());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "USER_DEACTIVATED",
				ENTITY_TYPE, event.userId(), AuditSnapshots.status("username", username, true),
				AuditSnapshots.status("username", username, false), null, username));
	}

}
