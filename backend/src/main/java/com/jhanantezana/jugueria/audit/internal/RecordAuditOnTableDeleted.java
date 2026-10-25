package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TableDeleted;

@Component
class RecordAuditOnTableDeleted {

	private final AuditLogService auditLog;

	RecordAuditOnTableDeleted(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(TableDeleted event) {
		auditLog.record(AuditEntry.deletion(event.occurredAt(), event.actorId(), event.actorRole(),
				TableAuditMaps.ENTITY_TYPE, event.tableId(), TableAuditMaps.of(event.before()), event.before().name(),
				null));
	}

}
