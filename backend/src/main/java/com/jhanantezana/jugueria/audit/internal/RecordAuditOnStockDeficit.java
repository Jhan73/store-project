package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.inventory.StockDeficit;

@Component
class RecordAuditOnStockDeficit {

	private static final String ENTITY_TYPE = "STOCK_ITEM";

	private final AuditLogService auditLog;

	private final AuditLabels labels;

	RecordAuditOnStockDeficit(AuditLogService auditLog, AuditLabels labels) {
		this.auditLog = auditLog;
		this.labels = labels;
	}

	@EventListener
	void on(StockDeficit event) {
		var after = new LinkedHashMap<String, Object>();
		after.put("onHand", event.onHand());
		after.put("shortBy", event.shortBy());
		after.put("referenceId", event.referenceId().toString());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), "STOCK_DEFICIT",
				ENTITY_TYPE, event.productId(), null, after, null,
				labels.product(event.productId())));
	}

}
