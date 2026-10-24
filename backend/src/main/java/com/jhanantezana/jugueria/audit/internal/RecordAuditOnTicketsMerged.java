package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TicketsMerged;

@Component
class RecordAuditOnTicketsMerged {

	private final AuditLogService auditLog;

	RecordAuditOnTicketsMerged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(TicketsMerged event) {
		var before = new LinkedHashMap<String, Object>();
		before.put("sourceTicketId", event.sourceTicketId().toString());
		before.put("sourceNumber", Long.toString(event.sourceNumber()));
		before.put("sourceTableId", event.sourceTableId() == null ? null : event.sourceTableId().toString());
		var after = new LinkedHashMap<String, Object>();
		after.put("movedLineIds", event.movedLineIds().stream().map(UUID::toString).toList());
		after.put("sourceStatus", "MERGED");
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "TICKETS_MERGED",
				RecordAuditOnTicketOpened.ENTITY_TYPE, event.targetTicketId(), before, after, null));
	}

}
