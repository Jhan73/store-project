package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Currency;
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
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.jugueria.store.DeliveryZoneCreated;
import com.jhanantezana.jugueria.store.DeliveryZoneSnapshot;
import com.jhanantezana.jugueria.store.DeliveryZoneStatusChanged;
import com.jhanantezana.jugueria.store.DeliveryZoneUpdated;
import com.jhanantezana.jugueria.store.OpeningHourSnapshot;
import com.jhanantezana.jugueria.store.OpeningHoursChanged;
import com.jhanantezana.jugueria.store.ReasonCreated;
import com.jhanantezana.jugueria.store.ReasonStatusChanged;
import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.StoreSettingsChanged;
import com.jhanantezana.jugueria.store.StoreSettingsSnapshot;

// One listener class per store event; this file exercises all of them against real Postgres in one Spring context.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class RecordAuditOnStoreEventsIT {

	static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

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
	void recordsAStoreSettingsChange() {
		var settingsId = UUID.randomUUID();
		var before = new StoreSettingsSnapshot("America/Lima", PEN, 10, 2, 15, 5, 10, Money.of("20.00", PEN), 3, 20);
		var after = new StoreSettingsSnapshot("America/Lima", PEN, 12, 3, 20, 6, 12, Money.of("30.00", PEN), 4, 25);
		var actorId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(status -> events
			.publishEvent(new StoreSettingsChanged(settingsId, before, after, actorId, Role.ADMIN, NOW)));

		var entry = findByEntityId(settingsId);
		assertThat(entry.getAction()).isEqualTo("STORE_SETTINGS_CHANGED");
		assertThat(entry.getActorId()).isEqualTo(actorId);
		assertThat(entry.getBefore()).containsEntry("basePrepMinutes", 10);
		assertThat(entry.getAfter()).containsEntry("basePrepMinutes", 12);
	}

	@Test
	void recordsAnOpeningHoursChange() {
		var settingsId = UUID.randomUUID();
		var before = java.util.List.of(new OpeningHourSnapshot(java.time.DayOfWeek.MONDAY, false,
				java.time.LocalTime.of(8, 0), java.time.LocalTime.of(22, 0)));
		var after = java.util.List.of(new OpeningHourSnapshot(java.time.DayOfWeek.MONDAY, true, null, null));

		transactionTemplate.executeWithoutResult(status -> events
			.publishEvent(new OpeningHoursChanged(settingsId, before, after, null, null, NOW)));

		var entry = findByEntityId(settingsId);
		assertThat(entry.getAction()).isEqualTo("OPENING_HOURS_CHANGED");
		assertThat(entry.getActorRole()).isEqualTo("SYSTEM");
		assertThat(entry.getAfter()).containsEntry("MONDAY", "CLOSED");
	}

	@Test
	void recordsADeliveryZoneCreation() {
		var zoneId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(status -> events
			.publishEvent(new DeliveryZoneCreated(zoneId, "Downtown", Money.of("5.00", PEN), 20, null, null, NOW)));

		var entry = findByEntityId(zoneId);
		assertThat(entry.getAction()).isEqualTo("DELIVERY_ZONE_CREATED");
		assertThat(entry.getAfter()).containsEntry("name", "Downtown");
	}

	@Test
	void recordsADeliveryZoneUpdate() {
		var zoneId = UUID.randomUUID();
		var before = new DeliveryZoneSnapshot("Downtown", Money.of("5.00", PEN), 20, null, null);
		var after = new DeliveryZoneSnapshot("Uptown", Money.of("8.00", PEN), 30, null, null);

		transactionTemplate.executeWithoutResult(
				status -> events.publishEvent(new DeliveryZoneUpdated(zoneId, before, after, null, null, NOW)));

		var entry = findByEntityId(zoneId);
		assertThat(entry.getAction()).isEqualTo("DELIVERY_ZONE_UPDATED");
		assertThat(entry.getBefore()).containsEntry("name", "Downtown");
		assertThat(entry.getAfter()).containsEntry("name", "Uptown");
	}

	@Test
	void recordsADeliveryZoneStatusChange() {
		var zoneId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(
				status -> events.publishEvent(new DeliveryZoneStatusChanged(zoneId, false, null, null, NOW)));

		var entry = findByEntityId(zoneId);
		assertThat(entry.getAction()).isEqualTo("DELIVERY_ZONE_STATUS_CHANGED");
		assertThat(entry.getAfter()).containsEntry("active", false);
	}

	@Test
	void recordsAReasonCreation() {
		var reasonId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(
				status -> events.publishEvent(new ReasonCreated(reasonId, ReasonType.VOID, "Wrong order", null, null, NOW)));

		var entry = findByEntityId(reasonId);
		assertThat(entry.getAction()).isEqualTo("REASON_CREATED");
		assertThat(entry.getAfter()).containsEntry("type", "VOID");
	}

	@Test
	void recordsAReasonStatusChange() {
		var reasonId = UUID.randomUUID();

		transactionTemplate.executeWithoutResult(
				status -> events.publishEvent(new ReasonStatusChanged(reasonId, true, null, null, NOW)));

		var entry = findByEntityId(reasonId);
		assertThat(entry.getAction()).isEqualTo("REASON_STATUS_CHANGED");
		assertThat(entry.getAfter()).containsEntry("active", true);
	}

	private AuditLog findByEntityId(UUID entityId) {
		return auditLogs.findAll()
			.stream()
			.filter(log -> log.getEntityId().equals(entityId))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No audit_log row for entity " + entityId));
	}

}
