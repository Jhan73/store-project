package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.PaymentRecorded;

@Component
class RecordAuditOnPaymentRecorded {

	static final String ENTITY_TYPE = "PAYMENT";

	private final AuditLogService auditLog;

	private final AuditLabels labels;

	RecordAuditOnPaymentRecorded(AuditLogService auditLog, AuditLabels labels) {
		this.auditLog = auditLog;
		this.labels = labels;
	}

	@EventListener
	void on(PaymentRecorded event) {
		var after = new LinkedHashMap<String, Object>();
		after.put("ticketId", event.ticketId().toString());
		after.put("shiftId", event.shiftId().toString());
		after.put("method", event.method().name());
		after.put("paid", event.paid().amount().toPlainString());
		after.put("received", event.received() == null ? null : event.received().amount().toPlainString());
		after.put("change", event.change() == null ? null : event.change().amount().toPlainString());
		after.put("lineIds", event.lineIds().stream().map(Object::toString).toList());
		after.put("ticketClosed", event.ticketClosed());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "PAYMENT_RECORDED",
				ENTITY_TYPE, event.paymentId(), null, after, null,
				labels.ticket(event.ticketId())));
	}

}
