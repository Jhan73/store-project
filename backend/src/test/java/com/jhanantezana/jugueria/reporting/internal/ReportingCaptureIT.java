package com.jhanantezana.jugueria.reporting.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.instore.CashMovementRecorded;
import com.jhanantezana.jugueria.instore.CashMovementType;
import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.instore.PaymentVoided;
import com.jhanantezana.jugueria.instore.SaleRecorded;
import com.jhanantezana.jugueria.instore.SaleVoided;
import com.jhanantezana.jugueria.instore.ShiftClosed;
import com.jhanantezana.jugueria.instore.ShiftOpened;
import com.jhanantezana.jugueria.instore.ShiftTotals;
import com.jhanantezana.jugueria.instore.SoldLine;
import com.jhanantezana.jugueria.instore.TicketLineVoided;
import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.InventoryTables;
import com.jhanantezana.testsupport.ReportingTables;

// Events are published inside a transaction so the after-commit listeners run; each test reads only its own IDs.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class ReportingCaptureIT {

	static final Instant NOW = Instant.parse("2026-10-13T02:30:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	JdbcClient jdbc;

	UUID cashier = UUID.randomUUID();

	@AfterEach
	void cleanUp() {
		ReportingTables.clean(jdbc);
		InventoryTables.clean(jdbc);
	}

	@Test
	void everySoldLineBecomesASalesLineInStoreTimeWithItsCategory() {
		var product = InventoryTables.aProduct(jdbc);
		var ticket = UUID.randomUUID();
		var line = soldLine(product, 2, "5.00", List.of("Large", "Chia"));

		publish(new SaleRecorded(ticket, 17, TicketType.TABLE, false, Money.of("10.00", PEN), List.of(line), cashier,
				Role.CASHIER, NOW));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			var rows = jdbc.sql("select * from reporting.sales_line where ticket_id = ?").param(ticket).query().listOfRows();
			assertThat(rows).singleElement().satisfies(row -> {
				assertThat(row).containsEntry("line_id", line.lineId())
					.containsEntry("product_id", product)
					.containsEntry("hour", 21)
					.containsEntry("weekday", 1)
					.containsEntry("channel", "IN_STORE")
					.containsEntry("ticket_type", "TABLE")
					.containsEntry("quick_sale", false)
					.containsEntry("quantity", 2)
					.containsEntry("voided", false)
					.containsEntry("employee_id", cashier);
				assertThat(row.get("business_date").toString()).isEqualTo("2026-10-12");
				assertThat(row.get("category_id")).isNotNull();
				assertThat(row.get("category_name")).isNotNull();
				assertThat(row.get("line_total_amount")).isEqualTo(new BigDecimal("10.00"));
				assertThat(row.get("modifiers").toString()).contains("Large").contains("Chia");
			});
		});
	}

	@Test
	void aRedeliveredSaleAddsNoSecondRow() {
		var ticket = UUID.randomUUID();
		var sale = new SaleRecorded(ticket, 18, TicketType.COUNTER, true, Money.of("5.00", PEN),
				List.of(soldLine(UUID.randomUUID(), 1, "5.00", List.of())), cashier, Role.CASHIER, NOW);

		publish(sale);
		publish(sale);

		await().during(Duration.ofSeconds(3))
			.atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("sales_line", "ticket_id", ticket)).isEqualTo(1));
	}

	@Test
	void aVoidedSaleProducesNegativeLinesKeepingTheTicketKind() {
		var ticket = UUID.randomUUID();
		var saleVoid = UUID.randomUUID();
		var line = soldLine(UUID.randomUUID(), 2, "5.00", List.of());

		publish(new SaleVoided(ticket, saleVoid, TicketType.COUNTER, true, Money.of("10.00", PEN), List.of(line),
				"Changed mind", null, cashier, Role.CASHIER, NOW));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			var rows = jdbc.sql("select * from reporting.sales_line where ticket_id = ?").param(ticket).query().listOfRows();
			assertThat(rows).singleElement().satisfies(row -> {
				assertThat(row).containsEntry("voided", true)
					.containsEntry("quantity", -2)
					.containsEntry("ticket_type", "COUNTER")
					.containsEntry("quick_sale", true)
					.containsEntry("source_id", saleVoid);
				assertThat(row.get("line_total_amount")).isEqualTo(new BigDecimal("-10.00"));
			});
		});
	}

	@Test
	void aLineVoidIsAnExceptionAndWasteIsTrackedApartFromIt() {
		var plain = UUID.randomUUID();
		var waste = UUID.randomUUID();

		publish(new TicketLineVoided(UUID.randomUUID(), plain, UUID.randomUUID(), 1, false, "Changed mind", "oops",
				cashier, Role.CASHIER, NOW));
		publish(new TicketLineVoided(UUID.randomUUID(), waste, UUID.randomUUID(), 1, true, "Spilled", null, cashier,
				Role.CASHIER, NOW));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			assertThat(exceptionTypes(plain)).containsExactly("LINE_VOID");
			assertThat(exceptionTypes(waste)).containsExactly("WASTE");
		});
		var row = jdbc.sql("select * from reporting.exception_event where source_id = ?")
			.param(plain)
			.query()
			.singleRow();
		assertThat(row).containsEntry("employee_id", cashier).containsEntry("reason_code", "Changed mind");
		assertThat(row.get("business_date").toString()).isEqualTo("2026-10-12");
	}

	@Test
	void aSaleVoidCountsOnceAndItsPaymentVoidsAreNotCountedAgain() {
		var saleVoid = UUID.randomUUID();
		var inSale = UUID.randomUUID();
		var alone = UUID.randomUUID();
		var ticket = UUID.randomUUID();

		publish(new SaleVoided(ticket, saleVoid, TicketType.TABLE, false, Money.of("10.00", PEN), List.of(),
				"Changed mind", null, cashier, Role.CASHIER, NOW));
		publish(new PaymentVoided(inSale, ticket, null, PaymentMethod.CASH, Money.of("10.00", PEN), "Changed mind", null,
				saleVoid, cashier, Role.CASHIER, NOW));
		publish(new PaymentVoided(alone, ticket, null, PaymentMethod.CARD, Money.of("4.00", PEN), "Wrong tender", null,
				null, cashier, Role.CASHIER, NOW));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			assertThat(exceptionTypes(saleVoid)).containsExactly("SALE_VOID");
			assertThat(exceptionTypes(alone)).containsExactly("PAYMENT_VOID");
		});
		assertThat(exceptionTypes(inSale)).isEmpty();
		var amount = jdbc.sql("select amount_amount from reporting.exception_event where source_id = ?")
			.param(alone)
			.query(BigDecimal.class)
			.single();
		assertThat(amount).isEqualByComparingTo("4.00");
	}

	@Test
	void onlyCashOutIsAnException() {
		var out = UUID.randomUUID();
		var in = UUID.randomUUID();
		var shift = UUID.randomUUID();

		publish(new CashMovementRecorded(out, shift, CashMovementType.OUT, Money.of("30.00", PEN), "Supplies", null, null,
				cashier, Role.CASHIER, NOW));
		publish(new CashMovementRecorded(in, shift, CashMovementType.IN, Money.of("50.00", PEN), "Float top-up", null,
				null, cashier, Role.CASHIER, NOW));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(exceptionTypes(out)).containsExactly("CASH_OUT"));
		assertThat(exceptionTypes(in)).isEmpty();
	}

	@Test
	void aShiftProducesOneSummaryWhateverTheOrderOfItsEvents() {
		var shift = UUID.randomUUID();
		var closed = counted(shift, "100.00", "95.00", "-5.00", "Short on change");

		publish(closed);
		publish(new ShiftOpened(shift, cashier, Money.of("100.00", PEN), cashier, Role.CASHIER,
				NOW.minus(Duration.ofHours(8))));
		publish(closed);

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			var rows = jdbc.sql("select * from reporting.shift_summary where shift_id = ?")
				.param(shift)
				.query()
				.listOfRows();
			assertThat(rows).singleElement().satisfies(row -> {
				assertThat(row).containsEntry("cashier_id", cashier).containsEntry("forced", false);
				assertThat(row.get("opened_at")).isNotNull();
				assertThat(row.get("closed_at")).isNotNull();
				assertThat(row.get("counted_amount")).isEqualTo(new BigDecimal("95.00"));
				assertThat(row.get("difference_amount")).isEqualTo(new BigDecimal("-5.00"));
				assertThat(row.get("by_method").toString()).contains("CASH");
			});
		});
	}

	@Test
	void aRegisterDifferenceIsAnExceptionOfTheCashierButAForcedCloseIsNot() {
		var short_ = UUID.randomUUID();
		var even = UUID.randomUUID();
		var forced = UUID.randomUUID();

		publish(counted(short_, "100.00", "95.00", "-5.00", "Short on change"));
		publish(counted(even, "100.00", "100.00", "0.00", null));
		publish(new ShiftClosed(forced, cashier, true, Money.of("100.00", PEN), null, Money.of("100.00", PEN), null, null,
				"Left early", totals(), UUID.randomUUID(), Role.ADMIN, NOW));

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			assertThat(exceptionTypes(short_)).containsExactly("SHIFT_DIFFERENCE");
			assertThat(count("shift_summary", "shift_id", forced)).isEqualTo(1);
			assertThat(count("shift_summary", "shift_id", even)).isEqualTo(1);
		});
		assertThat(exceptionTypes(even)).isEmpty();
		assertThat(exceptionTypes(forced)).isEmpty();
		var row = jdbc.sql("select * from reporting.exception_event where source_id = ?")
			.param(short_)
			.query()
			.singleRow();
		assertThat(row).containsEntry("employee_id", cashier).containsEntry("shift_id", short_);
		assertThat(row.get("amount_amount")).isEqualTo(new BigDecimal("-5.00"));
	}

	ShiftClosed counted(UUID shift, String opening, String counted, String difference, String note) {
		return new ShiftClosed(shift, cashier, false, Money.of(opening, PEN), Money.of(counted, PEN),
				Money.of("100.00", PEN), Money.of(difference, PEN), note, null, totals(), cashier, Role.CASHIER, NOW);
	}

	ShiftTotals totals() {
		var zero = Money.zero(PEN);
		return new ShiftTotals(zero, zero, zero, zero, zero,
				List.of(new ShiftTotals.MethodTotal(PaymentMethod.CASH, zero, zero, zero)));
	}

	static SoldLine soldLine(UUID product, int quantity, String unitPrice, List<String> modifiers) {
		var unit = Money.of(unitPrice, PEN);
		return new SoldLine(UUID.randomUUID(), product, "Orange juice", quantity, unit, unit.times(quantity), modifiers);
	}

	void publish(Object event) {
		transactions.executeWithoutResult(status -> events.publishEvent(event));
	}

	long count(String table, String column, UUID id) {
		return jdbc.sql("select count(*) from reporting." + table + " where " + column + " = ?")
			.param(id)
			.query(Long.class)
			.single();
	}

	List<String> exceptionTypes(UUID sourceId) {
		return jdbc.sql("select type from reporting.exception_event where source_id = ?")
			.param(sourceId)
			.query(String.class)
			.list();
	}

}
