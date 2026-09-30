package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.ProductChanged;

@Component
class RecordAuditOnProductChanged {

	private static final String ENTITY_TYPE = "PRODUCT";

	private final AuditLogService auditLog;

	RecordAuditOnProductChanged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(ProductChanged event) {
		var before = event.before() == null ? null : CatalogAuditMaps.toMap(event.before());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(),
				event.before() == null ? "PRODUCT_CREATED" : "PRODUCT_UPDATED", ENTITY_TYPE, event.productId(), before,
				CatalogAuditMaps.toMap(event.after()), null));
	}

}
