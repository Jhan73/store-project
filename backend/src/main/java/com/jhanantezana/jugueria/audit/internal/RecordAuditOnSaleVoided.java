package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.SaleVoided;

@Component
class RecordAuditOnSaleVoided {

	private final AuditLogService auditLog;

	private final AuditLabels labels;

	RecordAuditOnSaleVoided(AuditLogService auditLog, AuditLabels labels) {
		this.auditLog = auditLog;
		this.labels = labels;
	}

	@EventListener
	void on(SaleVoided event) {
		var after = new LinkedHashMap<String, Object>();
		after.put("voided", true);
		after.put("total", event.total().amount().toPlainString());
		after.put("saleVoidId", event.saleVoidId().toString());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "SALE_VOIDED",
				RecordAuditOnTicketOpened.ENTITY_TYPE, event.ticketId(), Map.of("status", "CLOSED"), after,
				reason(event.reasonCode(), event.note()), labels.ticket(event.ticketId())));
	}

	private static String reason(String code, @Nullable String note) {
		return note == null ? code : code + ": " + note;
	}

}
