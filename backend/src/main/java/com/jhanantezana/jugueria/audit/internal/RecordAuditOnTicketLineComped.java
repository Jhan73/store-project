package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.TicketLineComped;

@Component
class RecordAuditOnTicketLineComped {

	private static final String ENTITY_TYPE = "TICKET_LINE";

	private final AuditLogService auditLog;

	RecordAuditOnTicketLineComped(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(TicketLineComped event) {
		var after = new LinkedHashMap<String, Object>();
		after.put("state", "COMPED");
		after.put("waste", true);
		after.put("ticketId", event.ticketId().toString());
		after.put("productId", event.productId().toString());
		after.put("productName", event.productName());
		after.put("quantity", event.quantity());
		after.put("amount", event.amount().amount().toPlainString());
		after.put("currency", event.amount().currency().getCurrencyCode());
		after.put("ticketClosed", event.ticketClosed());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "TICKET_LINE_COMPED",
				ENTITY_TYPE, event.lineId(), Map.of("state", "SENT"), after, reason(event.reasonCode(), event.note()), event.productName()));
	}

	private static String reason(String code, @Nullable String note) {
		return note == null ? code : code + ": " + note;
	}

}
