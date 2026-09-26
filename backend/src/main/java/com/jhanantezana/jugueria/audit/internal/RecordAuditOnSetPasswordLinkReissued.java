package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.SetPasswordLinkReissued;

@Component
class RecordAuditOnSetPasswordLinkReissued {

	private static final String ENTITY_TYPE = "USER";

	private final AuditLogService auditLog;

	RecordAuditOnSetPasswordLinkReissued(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(SetPasswordLinkReissued event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				"SET_PASSWORD_LINK_REISSUED", ENTITY_TYPE, event.userId(), null, null, null));
	}

}
