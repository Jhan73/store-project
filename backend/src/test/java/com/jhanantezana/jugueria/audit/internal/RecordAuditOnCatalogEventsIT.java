package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.catalog.Allergen;
import com.jhanantezana.jugueria.catalog.ModifierGroupChanged;
import com.jhanantezana.jugueria.catalog.ModifierGroupSnapshot;
import com.jhanantezana.jugueria.catalog.ModifierOptionSnapshot;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

// One listener class per audited catalog event; this file exercises all of them against real Postgres.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class RecordAuditOnCatalogEventsIT {

	static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	AuditLogRepository auditLogs;

	@Autowired
	TransactionTemplate transactionTemplate;

	@BeforeEach
	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void recordsAModifierGroupCreation() {
		var groupId = UUID.randomUUID();
		var actorId = UUID.randomUUID();

		publish(new ModifierGroupChanged(groupId, null, group("Size", "0.00", Set.of(Allergen.MILK)), actorId,
				Role.ADMIN, NOW));

		var entry = findByEntityId(groupId);
		assertThat(entry.getAction()).isEqualTo("MODIFIER_GROUP_CREATED");
		assertThat(entry.getEntityType()).isEqualTo("MODIFIER_GROUP");
		assertThat(entry.getActorId()).isEqualTo(actorId);
		assertThat(entry.getBefore()).isNull();
		assertThat(entry.getAfter()).containsEntry("name", "Size").containsEntry("minChoices", 1);
		assertThat((List<?>) entry.getAfter().get("options")).singleElement()
			.satisfies(option -> assertThat(option.toString()).contains("priceDelta=0.00").contains("MILK"));
	}

	@Test
	void recordsAModifierGroupUpdateWithBothSides() {
		var groupId = UUID.randomUUID();

		publish(new ModifierGroupChanged(groupId, group("Size", "0.00", Set.of()), group("Sizes", "1.50", Set.of()),
				null, null, NOW));

		var entry = findByEntityId(groupId);
		assertThat(entry.getAction()).isEqualTo("MODIFIER_GROUP_UPDATED");
		assertThat(entry.getActorRole()).isEqualTo("SYSTEM");
		assertThat(entry.getBefore()).containsEntry("name", "Size");
		assertThat(entry.getAfter()).containsEntry("name", "Sizes");
	}

	@Test
	void recordsAModifierGroupDeletion() {
		var groupId = UUID.randomUUID();

		publish(new ModifierGroupChanged(groupId, group("Size", "0.00", Set.of()), null, null, null, NOW));

		var entry = findByEntityId(groupId);
		assertThat(entry.getAction()).isEqualTo("MODIFIER_GROUP_DELETED");
		assertThat(entry.getAfter()).isNull();
	}

	private void publish(Object event) {
		transactionTemplate.executeWithoutResult(status -> events.publishEvent(event));
	}

	private AuditLog findByEntityId(UUID entityId) {
		return auditLogs.findAll().stream().filter(entry -> entityId.equals(entry.getEntityId())).findFirst().orElseThrow();
	}

	private static ModifierGroupSnapshot group(String name, String delta, Set<Allergen> allergens) {
		return new ModifierGroupSnapshot(name, true, 1, 1, List.of(
				new ModifierOptionSnapshot(UUID.randomUUID(), "Small", Money.of(delta, PEN), true, allergens)));
	}

}
