package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.UserPasswordSet;

@Component
class RecordAuditOnUserPasswordSet {

	private static final String ENTITY_TYPE = "USER";

	private final AuditLogService auditLog;

	RecordAuditOnUserPasswordSet(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	// The set-password token is the actor's own proof of identity; never a request with a signed-in user.
	@EventListener
	void on(UserPasswordSet event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.userId(), event.role(), "USER_PASSWORD_SET",
				ENTITY_TYPE, event.userId(), null, null, null));
	}

}
