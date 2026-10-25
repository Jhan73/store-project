package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.UserReactivated;

@Component
class RecordAuditOnUserReactivated {

	private static final String ENTITY_TYPE = "USER";

	private final AuditLogService auditLog;

	private final AuditLabels labels;

	RecordAuditOnUserReactivated(AuditLogService auditLog, AuditLabels labels) {
		this.auditLog = auditLog;
		this.labels = labels;
	}

	@EventListener
	void on(UserReactivated event) {
		var username = labels.user(event.userId());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "USER_REACTIVATED",
				ENTITY_TYPE, event.userId(), AuditSnapshots.status("username", username, false),
				AuditSnapshots.status("username", username, true), null, username));
	}

}
