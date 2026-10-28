package com.jhanantezana.jugueria.audit.internal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.instore.WalletProviderChanged;
import com.jhanantezana.jugueria.instore.WalletProviderSnapshot;

@Component
class RecordAuditOnWalletProviderChanged {

	private static final String ENTITY_TYPE = "WALLET_PROVIDER";

	private final AuditLogService auditLog;

	RecordAuditOnWalletProviderChanged(AuditLogService auditLog) {
		this.auditLog = auditLog;
	}

	@EventListener
	void on(WalletProviderChanged event) {
		var before = event.before() == null ? null : toMap(event.before());
		auditLog.record(new AuditEntry(event.occurredAt(), event.actorId(), event.actorRole(), action(event),
				ENTITY_TYPE, event.providerId(), before, toMap(event.after()), null, event.after().name()));
	}

	private static String action(WalletProviderChanged event) {
		return switch (event.kind()) {
			case CREATED -> "WALLET_PROVIDER_CREATED";
			case UPDATED -> "WALLET_PROVIDER_UPDATED";
			case ACTIVATED -> "WALLET_PROVIDER_REACTIVATED";
			case DEACTIVATED -> "WALLET_PROVIDER_DEACTIVATED";
		};
	}

	private static Map<String, Object> toMap(WalletProviderSnapshot snapshot) {
		var map = new LinkedHashMap<String, Object>();
		map.put("name", snapshot.name());
		map.put("active", snapshot.active());
		return map;
	}

}
