package com.jhanantezana.jugueria.audit.internal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.catalog.CategoryChanged;

@Component
class RecordAuditOnCategoryChanged {

	private static final String ENTITY_TYPE = "CATEGORY";

	private final AuditLogService auditLog;

	RecordAuditOnCategoryChanged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(CategoryChanged event) {
		var before = event.before() == null ? null : CatalogAuditMaps.toMap(event.before());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), action(event),
				ENTITY_TYPE, event.categoryId(), before, CatalogAuditMaps.toMap(event.after()), null));
	}

	private static String action(CategoryChanged event) {
		return switch (event.kind()) {
			case CREATED -> "CATEGORY_CREATED";
			case UPDATED -> "CATEGORY_UPDATED";
			case DEACTIVATED -> "CATEGORY_DEACTIVATED";
			case ACTIVATED -> "CATEGORY_REACTIVATED";
		};
	}

}
