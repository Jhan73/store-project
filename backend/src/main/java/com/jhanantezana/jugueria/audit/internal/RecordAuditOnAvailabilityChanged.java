package com.jhanantezana.jugueria.audit.internal;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.AvailabilityChanged;

@Component
class RecordAuditOnAvailabilityChanged {

	private final AuditLogService auditLog;

	RecordAuditOnAvailabilityChanged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	// The event exists only when the flag flipped, so the previous value is always the opposite one.
	@EventListener
	void on(AvailabilityChanged event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				event.target().name() + "_AVAILABILITY_CHANGED", event.target().name(), event.targetId(),
				Map.of("available", !event.available()), Map.of("available", event.available()), null));
	}

}
