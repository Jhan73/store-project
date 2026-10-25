package com.jhanantezana.jugueria.audit.internal;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.UserRenamed;

@Component
class RecordAuditOnUserRenamed {

	private static final String ENTITY_TYPE = "USER";

	private final AuditLogService auditLog;

	RecordAuditOnUserRenamed(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(UserRenamed event) {
		var before = event.oldUsername() == null ? null : Map.<String, Object>of("username", event.oldUsername());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "USER_RENAMED",
				ENTITY_TYPE, event.userId(), before, Map.of("username", event.newUsername()), null,
				event.newUsername()));
	}

}
