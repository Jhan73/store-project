package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.inventory.StockAdjusted;
import com.jhanantezana.jugueria.inventory.StockSnapshot;

@Component
class RecordAuditOnStockAdjusted {

	private static final String ENTITY_TYPE = "STOCK_ITEM";

	private final AuditLogService auditLog;

	RecordAuditOnStockAdjusted(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(StockAdjusted event) {
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				"STOCK_" + event.kind().name(), ENTITY_TYPE, event.productId(), of(event.before()), of(event.after()),
				reason(event.reasonCode(), event.note())));
	}

	private static @Nullable String reason(@Nullable String code, @Nullable String note) {
		if (code == null) {
			return note;
		}
		return note == null ? code : code + ": " + note;
	}

	private static Map<String, Object> of(StockSnapshot snapshot) {
		var map = new LinkedHashMap<String, Object>();
		map.put("tracked", snapshot.tracked());
		map.put("onHand", snapshot.onHand());
		map.put("reserved", snapshot.reserved());
		map.put("lowStockThreshold", snapshot.lowStockThreshold());
		return map;
	}

}
