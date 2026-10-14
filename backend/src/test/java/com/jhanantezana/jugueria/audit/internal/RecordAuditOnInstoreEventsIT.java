package com.jhanantezana.jugueria.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
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
import com.jhanantezana.jugueria.instore.CashMovementRecorded;
import com.jhanantezana.jugueria.instore.CashMovementType;
import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.instore.PaymentRecorded;
import com.jhanantezana.jugueria.instore.PaymentVoided;
import com.jhanantezana.jugueria.instore.SaleVoided;
import com.jhanantezana.jugueria.instore.ShiftClosed;
import com.jhanantezana.jugueria.instore.ShiftCountRevealed;
import com.jhanantezana.jugueria.instore.ShiftOpened;
import com.jhanantezana.jugueria.instore.ShiftTotals;
import com.jhanantezana.jugueria.instore.TableCreated;
import com.jhanantezana.jugueria.instore.TableSnapshot;
import com.jhanantezana.jugueria.instore.TableStatusChanged;
import com.jhanantezana.jugueria.instore.TableUpdated;
import com.jhanantezana.jugueria.instore.TicketLineVoided;
import com.jhanantezana.jugueria.instore.TicketLinesSent;
import com.jhanantezana.jugueria.instore.TicketOpened;
import com.jhanantezana.jugueria.instore.TicketTransferred;
import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;

// One listener class per audited instore event; this file exercises all of them against real Postgres.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class RecordAuditOnInstoreEventsIT {

	static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

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
	void recordsATableCreation() {
		var tableId = UUID.randomUUID();
		var actorId = UUID.randomUUID();

		publish(new TableCreated(tableId, new TableSnapshot("Mesa 1", "Terrace", 2), actorId, Role.ADMIN, NOW));

		var entry = findByEntityId(tableId);
		assertThat(entry.getAction()).isEqualTo("TABLE_CREATED");
		assertThat(entry.getEntityType()).isEqualTo("TABLE");
		assertThat(entry.getActorId()).isEqualTo(actorId);
		assertThat(entry.getActorRole()).isEqualTo("ADMIN");
		assertThat(entry.getBefore()).isNull();
		assertThat(entry.getAfter()).containsEntry("name", "Mesa 1")
			.containsEntry("area", "Terrace")
			.containsEntry("displayOrder", 2);
	}

	@Test
	void recordsATableUpdateWithBothSides() {
		var tableId = UUID.randomUUID();

		publish(new TableUpdated(tableId, new TableSnapshot("Mesa 1", "Terrace", 2), new TableSnapshot("Barra", null, 5),
				null, null, NOW));

		var entry = findByEntityId(tableId);
		assertThat(entry.getAction()).isEqualTo("TABLE_UPDATED");
		assertThat(entry.getActorRole()).isEqualTo("SYSTEM");
		assertThat(entry.getBefore()).containsEntry("name", "Mesa 1").containsEntry("area", "Terrace");
		assertThat(entry.getAfter()).containsEntry("name", "Barra").containsEntry("area", null);
	}

	@Test
	void recordsATableStatusChange() {
		var tableId = UUID.randomUUID();
		var actorId = UUID.randomUUID();

		publish(new TableStatusChanged(tableId, false, actorId, Role.ADMIN, NOW));

		var entry = findByEntityId(tableId);
		assertThat(entry.getAction()).isEqualTo("TABLE_STATUS_CHANGED");
		assertThat(entry.getEntityType()).isEqualTo("TABLE");
		assertThat(entry.getActorId()).isEqualTo(actorId);
		assertThat(entry.getAfter()).containsEntry("active", false);
	}

	@Test
	void recordsATicketBeingOpened() {
		var ticketId = UUID.randomUUID();
		var tableId = UUID.randomUUID();
		var actorId = UUID.randomUUID();

		publish(new TicketOpened(ticketId, 12, TicketType.TABLE, tableId, null, actorId, Role.SERVER, NOW));

		var entry = findByEntityId(ticketId);
		assertThat(entry.getAction()).isEqualTo("TICKET_OPENED");
		assertThat(entry.getEntityType()).isEqualTo("TICKET");
		assertThat(entry.getActorId()).isEqualTo(actorId);
		assertThat(entry.getActorRole()).isEqualTo("SERVER");
		assertThat(entry.getBefore()).isNull();
		assertThat(entry.getAfter()).containsEntry("number", "12")
			.containsEntry("type", "TABLE")
			.containsEntry("tableId", tableId.toString())
			.containsEntry("counterName", null);
	}

	@Test
	void recordsLinesBeingSent() {
		var ticketId = UUID.randomUUID();
		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		var source = UUID.randomUUID();

		publish(new TicketLinesSent(ticketId, source, List.of(first, second), UUID.randomUUID(), Role.CASHIER, NOW));

		var entry = findByEntityId(ticketId);
		assertThat(entry.getAction()).isEqualTo("TICKET_LINES_SENT");
		assertThat(entry.getEntityType()).isEqualTo("TICKET");
		assertThat(entry.getAfter()).containsEntry("boardSourceId", source.toString())
			.containsEntry("lineIds", List.of(first.toString(), second.toString()));
	}

	@Test
	void recordsALineVoidWithItsReasonAndWaste() {
		var ticketId = UUID.randomUUID();
		var lineId = UUID.randomUUID();
		var product = UUID.randomUUID();

		publish(new TicketLineVoided(ticketId, lineId, product, 2, true, "Changed mind", "Wanted strawberry",
				UUID.randomUUID(), Role.CASHIER, NOW));

		var entry = findByEntityId(lineId);
		assertThat(entry.getAction()).isEqualTo("TICKET_LINE_VOIDED");
		assertThat(entry.getEntityType()).isEqualTo("TICKET_LINE");
		assertThat(entry.getReason()).isEqualTo("Changed mind: Wanted strawberry");
		assertThat(entry.getBefore()).containsEntry("state", "SENT");
		assertThat(entry.getAfter()).containsEntry("state", "VOIDED")
			.containsEntry("waste", true)
			.containsEntry("ticketId", ticketId.toString())
			.containsEntry("productId", product.toString())
			.containsEntry("quantity", 2);
	}

	@Test
	void recordsALineVoidWithoutANote() {
		var lineId = UUID.randomUUID();

		publish(new TicketLineVoided(UUID.randomUUID(), lineId, UUID.randomUUID(), 1, false, "Changed mind", null,
				UUID.randomUUID(), Role.SERVER, NOW));

		assertThat(findByEntityId(lineId).getReason()).isEqualTo("Changed mind");
	}

	@Test
	void recordsATicketTransfer() {
		var ticketId = UUID.randomUUID();
		var from = UUID.randomUUID();
		var to = UUID.randomUUID();

		publish(new TicketTransferred(ticketId, from, to, UUID.randomUUID(), Role.SERVER, NOW));

		var entry = findByEntityId(ticketId);
		assertThat(entry.getAction()).isEqualTo("TICKET_TRANSFERRED");
		assertThat(entry.getEntityType()).isEqualTo("TICKET");
		assertThat(entry.getBefore()).containsEntry("tableId", from.toString());
		assertThat(entry.getAfter()).containsEntry("tableId", to.toString());
	}

	@Test
	void recordsAShiftBeingOpened() {
		var shiftId = UUID.randomUUID();
		var cashier = UUID.randomUUID();

		publish(new ShiftOpened(shiftId, cashier, Money.of("100.00", PEN), cashier, Role.CASHIER, NOW));

		var entry = findByEntityId(shiftId);
		assertThat(entry.getAction()).isEqualTo("SHIFT_OPENED");
		assertThat(entry.getEntityType()).isEqualTo("SHIFT");
		assertThat(entry.getActorId()).isEqualTo(cashier);
		assertThat(entry.getBefore()).isNull();
		assertThat(entry.getAfter()).containsEntry("cashierId", cashier.toString()).containsEntry("openingFloat", "100.00");
	}

	@Test
	void recordsACountedShiftCloseWithTheDifferenceAndItsNote() {
		var shiftId = UUID.randomUUID();
		var cashier = UUID.randomUUID();

		publish(new ShiftClosed(shiftId, cashier, false, Money.of("100.00", PEN), Money.of("60.00", PEN),
				Money.of("100.00", PEN), Money.of("-40.00", PEN), "Paid the delivery boy", null,
				ShiftTotals.empty(PEN), cashier, Role.CASHIER, NOW));

		var entry = findByEntityId(shiftId);
		assertThat(entry.getAction()).isEqualTo("SHIFT_CLOSED");
		assertThat(entry.getEntityType()).isEqualTo("SHIFT");
		assertThat(entry.getReason()).isEqualTo("Paid the delivery boy");
		assertThat(entry.getBefore()).containsEntry("status", "OPEN");
		assertThat(entry.getAfter()).containsEntry("status", "CLOSED")
			.containsEntry("kind", "COUNTED")
			.containsEntry("countedCash", "60.00")
			.containsEntry("expectedCash", "100.00")
			.containsEntry("difference", "-40.00");
	}

	@Test
	void recordsTheRevealOfACountOverTheThreshold() {
		var shiftId = UUID.randomUUID();
		var cashier = UUID.randomUUID();

		publish(new ShiftCountRevealed(shiftId, cashier, Money.of("60.00", PEN), Money.of("-40.00", PEN), cashier,
				Role.CASHIER, NOW));

		var entry = findByEntityId(shiftId);
		assertThat(entry.getAction()).isEqualTo("SHIFT_COUNT_REVEALED");
		assertThat(entry.getEntityType()).isEqualTo("SHIFT");
		assertThat(entry.getAfter()).containsEntry("countedCash", "60.00").containsEntry("difference", "-40.00");
	}

	@Test
	void recordsAForcedShiftCloseWithItsReason() {
		var shiftId = UUID.randomUUID();

		publish(new ShiftClosed(shiftId, UUID.randomUUID(), true, Money.of("100.00", PEN), null, Money.of("100.00", PEN),
				null, null, "Forgotten overnight", ShiftTotals.empty(PEN), UUID.randomUUID(), Role.ADMIN, NOW));

		var entry = findByEntityId(shiftId);
		assertThat(entry.getReason()).isEqualTo("Forgotten overnight");
		assertThat(entry.getAfter()).containsEntry("kind", "FORCED").containsEntry("countedCash", null);
	}

	@Test
	void recordsACashMovementWithItsReason() {
		var movementId = UUID.randomUUID();
		var shiftId = UUID.randomUUID();

		publish(new CashMovementRecorded(movementId, shiftId, CashMovementType.OUT, Money.of("35.50", PEN), "Supplier",
				"Mangoes", "INV-77", UUID.randomUUID(), Role.CASHIER, NOW));

		var entry = findByEntityId(movementId);
		assertThat(entry.getAction()).isEqualTo("CASH_MOVEMENT_RECORDED");
		assertThat(entry.getEntityType()).isEqualTo("CASH_MOVEMENT");
		assertThat(entry.getReason()).isEqualTo("Supplier: Mangoes");
		assertThat(entry.getAfter()).containsEntry("shiftId", shiftId.toString())
			.containsEntry("type", "OUT")
			.containsEntry("amount", "35.50")
			.containsEntry("reference", "INV-77");
	}

	@Test
	void recordsACashInMovementWithItsNoteAsTheReason() {
		var movementId = UUID.randomUUID();

		publish(new CashMovementRecorded(movementId, UUID.randomUUID(), CashMovementType.IN, Money.of("20.00", PEN), null,
				"Change from the bank", null, UUID.randomUUID(), Role.CASHIER, NOW));

		assertThat(findByEntityId(movementId).getReason()).isEqualTo("Change from the bank");
	}

	@Test
	void recordsAPayment() {
		var paymentId = UUID.randomUUID();
		var ticketId = UUID.randomUUID();
		var line = UUID.randomUUID();

		publish(new PaymentRecorded(paymentId, ticketId, UUID.randomUUID(), PaymentMethod.CASH, Money.of("5.00", PEN),
				Money.of("10.00", PEN), Money.of("5.00", PEN), List.of(line), true, UUID.randomUUID(), Role.CASHIER, NOW));

		var entry = findByEntityId(paymentId);
		assertThat(entry.getAction()).isEqualTo("PAYMENT_RECORDED");
		assertThat(entry.getEntityType()).isEqualTo("PAYMENT");
		assertThat(entry.getAfter()).containsEntry("ticketId", ticketId.toString())
			.containsEntry("method", "CASH")
			.containsEntry("paid", "5.00")
			.containsEntry("received", "10.00")
			.containsEntry("change", "5.00")
			.containsEntry("lineIds", List.of(line.toString()))
			.containsEntry("ticketClosed", true);
	}

	@Test
	void recordsAPaymentVoidWithItsReason() {
		var paymentId = UUID.randomUUID();

		publish(new PaymentVoided(paymentId, UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.CARD, Money.of("5.00", PEN),
				"Changed mind", "Wrong method", null, UUID.randomUUID(), Role.CASHIER, NOW));

		var entry = findByEntityId(paymentId);
		assertThat(entry.getAction()).isEqualTo("PAYMENT_VOIDED");
		assertThat(entry.getReason()).isEqualTo("Changed mind: Wrong method");
		assertThat(entry.getBefore()).containsEntry("state", "RECORDED");
		assertThat(entry.getAfter()).containsEntry("state", "VOIDED").containsEntry("amount", "5.00");
	}

	@Test
	void recordsASaleVoid() {
		var ticketId = UUID.randomUUID();
		var saleVoid = UUID.randomUUID();

		publish(new SaleVoided(ticketId, saleVoid, TicketType.COUNTER, true, Money.of("10.00", PEN), List.of(), "Changed mind", null,
				UUID.randomUUID(), Role.ADMIN, NOW));

		var entry = findByEntityId(ticketId);
		assertThat(entry.getAction()).isEqualTo("SALE_VOIDED");
		assertThat(entry.getEntityType()).isEqualTo("TICKET");
		assertThat(entry.getReason()).isEqualTo("Changed mind");
		assertThat(entry.getBefore()).containsEntry("status", "CLOSED");
		assertThat(entry.getAfter()).containsEntry("voided", true)
			.containsEntry("total", "10.00")
			.containsEntry("saleVoidId", saleVoid.toString());
	}

	private void publish(Object event) {
		transactionTemplate.executeWithoutResult(status -> events.publishEvent(event));
	}

	private AuditLog findByEntityId(UUID entityId) {
		return auditLogs.findAll().stream().filter(entry -> entityId.equals(entry.getEntityId())).findFirst().orElseThrow();
	}

}
