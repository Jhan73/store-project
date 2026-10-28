package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.PaymentVoided;

@Component
class RecordAuditOnPaymentVoided {

	private final AuditLogService auditLog;

	private final AuditLabels labels;

	RecordAuditOnPaymentVoided(AuditLogService auditLog, AuditLabels labels) {
		this.auditLog = auditLog;
		this.labels = labels;
	}

	@EventListener
	void on(PaymentVoided event) {
		var after = new LinkedHashMap<String, Object>();
		after.put("state", "VOIDED");
		after.put("ticketId", event.ticketId().toString());
		after.put("shiftId", event.shiftId() == null ? null : event.shiftId().toString());
		after.put("method", event.method().name());
		after.put("walletProvider", event.walletProviderName());
		after.put("amount", event.amount().amount().toPlainString());
		after.put("saleVoidId", event.saleVoidId() == null ? null : event.saleVoidId().toString());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "PAYMENT_VOIDED",
				RecordAuditOnPaymentRecorded.ENTITY_TYPE, event.paymentId(), Map.of("state", "RECORDED"), after,
				reason(event.reasonCode(), event.note()),
				labels.ticket(event.ticketId())));
	}

	private static String reason(String code, @Nullable String note) {
		return note == null ? code : code + ": " + note;
	}

}
