package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jhanantezana.jugueria.shared.Role;

class AuditEntryTest {

	static final Instant NOW = Instant.parse("2026-10-24T09:00:00Z");

	@Test
	void aDeletionNamesItsActionAfterTheEntityTypeAndKeepsTheWholeSnapshotInBefore() {
		var entityId = UUID.randomUUID();
		var actorId = UUID.randomUUID();
		var snapshot = Map.<String, Object>of("name", "Mango juice", "price", "12.50");

		var entry = AuditEntry.deletion(NOW, actorId, Role.ADMIN, "PRODUCT", entityId, snapshot, "Mango juice", "Duplicate");

		assertThat(entry.action()).isEqualTo("PRODUCT_DELETED");
		assertThat(entry.entityType()).isEqualTo("PRODUCT");
		assertThat(entry.entityId()).isEqualTo(entityId);
		assertThat(entry.before()).isEqualTo(snapshot);
		assertThat(entry.after()).isNull();
		assertThat(entry.reason()).isEqualTo("Duplicate");
		assertThat(entry.entityLabel()).isEqualTo("Mango juice");
	}

}
