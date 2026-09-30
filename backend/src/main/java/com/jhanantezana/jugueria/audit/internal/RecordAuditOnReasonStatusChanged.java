package com.jhanantezana.jugueria.audit.internal;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.store.ReasonStatusChanged;

@Component
class RecordAuditOnReasonStatusChanged {

	private static final String ENTITY_TYPE = "REASON";

	private final AuditLogService auditLog;

	RecordAuditOnReasonStatusChanged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(ReasonStatusChanged event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				"REASON_STATUS_CHANGED", ENTITY_TYPE, event.reasonId(), null, Map.of("active", event.active()),
				null));
	}

}
