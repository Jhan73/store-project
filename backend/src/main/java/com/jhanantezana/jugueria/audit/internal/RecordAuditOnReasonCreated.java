package com.jhanantezana.jugueria.audit.internal;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.store.ReasonCreated;

@Component
class RecordAuditOnReasonCreated {

	private static final String ENTITY_TYPE = "REASON";

	private final AuditLogService auditLog;

	RecordAuditOnReasonCreated(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(ReasonCreated event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "REASON_CREATED",
				ENTITY_TYPE, event.reasonId(), null, Map.of("type", event.type().name(), "code", event.code()),
				null));
	}

}
