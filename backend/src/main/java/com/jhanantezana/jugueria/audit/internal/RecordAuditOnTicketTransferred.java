package com.jhanantezana.jugueria.audit.internal;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TicketTransferred;

@Component
class RecordAuditOnTicketTransferred {

	private final AuditLogService auditLog;

	private final AuditLabels labels;

	RecordAuditOnTicketTransferred(AuditLogService auditLog, AuditLabels labels) {
		this.auditLog = auditLog;
		this.labels = labels;
	}

	@EventListener
	void on(TicketTransferred event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "TICKET_TRANSFERRED",
				RecordAuditOnTicketOpened.ENTITY_TYPE, event.ticketId(), Map.of("tableId", event.fromTableId().toString()),
				Map.of("tableId", event.toTableId().toString()), null,
				labels.ticket(event.ticketId())));
	}

}
