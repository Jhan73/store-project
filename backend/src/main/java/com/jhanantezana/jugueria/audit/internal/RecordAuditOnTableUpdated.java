package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TableUpdated;

@Component
class RecordAuditOnTableUpdated {

	private final AuditLogService auditLog;

	RecordAuditOnTableUpdated(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(TableUpdated event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "TABLE_UPDATED",
				TableAuditMaps.ENTITY_TYPE, event.tableId(), TableAuditMaps.of(event.before()),
				TableAuditMaps.of(event.after()), null));
	}

}
