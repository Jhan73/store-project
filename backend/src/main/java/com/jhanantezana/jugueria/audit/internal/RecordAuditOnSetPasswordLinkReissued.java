package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.identity.SetPasswordLinkReissued;

@Component
class RecordAuditOnSetPasswordLinkReissued {

	private static final String ENTITY_TYPE = "USER";

	private final AuditLogService auditLog;

	private final AuditLabels labels;

	RecordAuditOnSetPasswordLinkReissued(AuditLogService auditLog, AuditLabels labels) {
		this.auditLog = auditLog;
		this.labels = labels;
	}

	@EventListener
	void on(SetPasswordLinkReissued event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				"SET_PASSWORD_LINK_REISSUED", ENTITY_TYPE, event.userId(), null, null, null,
				labels.user(event.userId())));
	}

}
