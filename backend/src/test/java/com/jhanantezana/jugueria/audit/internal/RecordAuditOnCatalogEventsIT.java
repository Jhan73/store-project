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
import com.jhanantezana.jugueria.catalog.AvailabilityChanged;
import com.jhanantezana.jugueria.catalog.AvailabilityTarget;
import com.jhanantezana.jugueria.catalog.ModifierGroupChanged;
import com.jhanantezana.jugueria.catalog.ModifierGroupSnapshot;
import com.jhanantezana.jugueria.catalog.ModifierOptionSnapshot;
import com.jhanantezana.jugueria.catalog.ProductChanged;
import com.jhanantezana.jugueria.catalog.ProductSnapshot;
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

	@Test
	void recordsAProductCreation() {
		var productId = UUID.randomUUID();
		var actorId = UUID.randomUUID();

		publish(new ProductChanged(productId, null, product("Mango", "12.50", true), actorId, Role.ADMIN, NOW));

		var entry = findByEntityId(productId);
		assertThat(entry.getAction()).isEqualTo("PRODUCT_CREATED");
		assertThat(entry.getEntityType()).isEqualTo("PRODUCT");
		assertThat(entry.getActorId()).isEqualTo(actorId);
		assertThat(entry.getBefore()).isNull();
		assertThat(entry.getAfter()).containsEntry("name", "Mango").containsEntry("price", "12.50");
	}

	@Test
	void recordsAPriceChangeWithBothPrices() {
		var productId = UUID.randomUUID();

		publish(new ProductChanged(productId, product("Mango", "12.50", true), product("Mango", "14.00", true), null,
				null, NOW));

		var entry = findByEntityId(productId);
		assertThat(entry.getAction()).isEqualTo("PRODUCT_UPDATED");
		assertThat(entry.getActorRole()).isEqualTo("SYSTEM");
		assertThat(entry.getBefore()).containsEntry("price", "12.50");
		assertThat(entry.getAfter()).containsEntry("price", "14.00");
	}

	@Test
	void recordsADeactivationAsAnUpdate() {
		var productId = UUID.randomUUID();

		publish(new ProductChanged(productId, product("Mango", "12.50", true), product("Mango", "12.50", false), null,
				null, NOW));

		var entry = findByEntityId(productId);
		assertThat(entry.getBefore()).containsEntry("active", true);
		assertThat(entry.getAfter()).containsEntry("active", false);
	}

	@Test
	void recordsAProductBeingMarkedUnavailable() {
		var productId = UUID.randomUUID();
		var actorId = UUID.randomUUID();

		publish(new AvailabilityChanged(productId, AvailabilityTarget.PRODUCT, false, actorId, Role.SERVER, NOW));

		var entry = findByEntityId(productId);
		assertThat(entry.getAction()).isEqualTo("PRODUCT_AVAILABILITY_CHANGED");
		assertThat(entry.getEntityType()).isEqualTo("PRODUCT");
		assertThat(entry.getActorId()).isEqualTo(actorId);
		assertThat(entry.getActorRole()).isEqualTo("SERVER");
		assertThat(entry.getBefore()).containsEntry("available", true);
		assertThat(entry.getAfter()).containsEntry("available", false);
	}

	@Test
	void recordsAnOptionBeingMarkedAvailableAgain() {
		var optionId = UUID.randomUUID();

		publish(new AvailabilityChanged(optionId, AvailabilityTarget.MODIFIER_OPTION, true, null, null, NOW));

		var entry = findByEntityId(optionId);
		assertThat(entry.getAction()).isEqualTo("MODIFIER_OPTION_AVAILABILITY_CHANGED");
		assertThat(entry.getEntityType()).isEqualTo("MODIFIER_OPTION");
		assertThat(entry.getBefore()).containsEntry("available", false);
		assertThat(entry.getAfter()).containsEntry("available", true);
	}

	private static ProductSnapshot product(String name, String price, boolean active) {
		return new ProductSnapshot(name, null, UUID.randomUUID(), Money.of(price, PEN), 0, false, active, null,
				Set.of(Allergen.MILK), List.of());
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
