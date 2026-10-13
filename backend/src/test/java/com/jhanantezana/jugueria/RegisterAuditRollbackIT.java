package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.audit.internal.AuditLog;
import com.jhanantezana.jugueria.audit.internal.AuditLogRepository;
import com.jhanantezana.jugueria.inventory.internal.StockService;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.InventoryTables;
import com.jhanantezana.testsupport.PreparationTables;

// Same guarantee as InstoreAuditRollbackIT for every audited register command: no change commits without its audit row.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegisterAuditRollbackIT {

	static final String SHIFTS = "/api/v1/shifts";

	static final String TICKETS = "/api/v1/tickets";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	StockService stock;

	@MockitoBean
	AuditLogRepository auditLogs;

	UUID cashier = UUID.randomUUID();

	@BeforeEach
	void auditWritesSucceedWhileSettingUp() {
		reset(auditLogs);
		InstoreTables.aVoidReason(jdbc, "Changed mind");
		InstoreTables.aCashOutReason(jdbc, "Supplier");
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void anAuditWriteFailureRollsBackAShiftOpening() {
		auditWritesNowFail();

		var result = openShift(cashier);

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(InstoreTables.count(jdbc, "register_shift")).isZero();
	}

	@Test
	void anAuditWriteFailureRollsBackACashMovement() {
		var shift = idOf(openShift(cashier));
		auditWritesNowFail();

		var result = mvc.post()
			.uri(SHIFTS + "/" + shift + "/cash-movements")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"OUT\",\"amount\":{\"amount\":\"5.00\",\"currency\":\"PEN\"},\"reasonCode\":\"Supplier\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(count("select count(*) from instore.cash_movement where shift_id = ?", UUID.fromString(shift))).isZero();
	}

	@Test
	void anAuditWriteFailureRollsBackAShiftClose() {
		var shift = idOf(openShift(cashier));
		auditWritesNowFail();

		var result = mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(status(shift)).isEqualTo("OPEN");
	}

	@Test
	void anAuditWriteFailureRollsBackTheRevealOfACount() {
		var shift = idOf(openShift(cashier));
		auditWritesNowFail();

		var result = mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"20.00\",\"currency\":\"PEN\"}}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(jdbc.sql("select counted_amount is null from instore.register_shift where id = ?")
			.param(UUID.fromString(shift))
			.query(Boolean.class)
			.single()).isTrue();
	}

	@Test
	void anAuditWriteFailureRollsBackAForcedShiftClose() {
		var shift = idOf(openShift(cashier));
		auditWritesNowFail();

		var result = mvc.post()
			.uri(SHIFTS + "/" + shift + "/force-close")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Forgotten\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(status(shift)).isEqualTo("OPEN");
	}

	@Test
	void anAuditWriteFailureRollsBackAPaymentThatWouldHaveClosedTheTicketAndItsStock() {
		openShift(cashier);
		var bottle = trackedProduct(5);
		var ticket = sentTicket(bottle, 2);
		auditWritesNowFail();

		var result = pay(ticket, "10.00");

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(count("select count(*) from instore.in_store_payment where ticket_id = ?", ticket)).isZero();
		assertThat(InstoreTables.ticketStatus(jdbc, ticket)).isEqualTo("OPEN");
		assertThat(stock.get(bottle).onHand()).isEqualTo(5);
	}

	@Test
	void anAuditWriteFailureRollsBackAPaymentVoid() {
		openShift(cashier);
		var ticket = sentTicket(trackedProduct(5), 2);
		var payment = paymentId(pay(ticket, "4.00"));
		auditWritesNowFail();

		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/payments/" + payment + "/void")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(count("select count(*) from instore.payment_void where ticket_id = ?", ticket)).isZero();
	}

	@Test
	void anAuditWriteFailureRollsBackASaleVoidAndItsStockReturn() {
		openShift(cashier);
		var bottle = trackedProduct(5);
		var ticket = sentTicket(bottle, 2);
		pay(ticket, "10.00");
		assertThat(stock.get(bottle).onHand()).isEqualTo(3);
		auditWritesNowFail();

		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/void")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(count("select count(*) from instore.sale_void where ticket_id = ?", ticket)).isZero();
		assertThat(count("select count(*) from instore.payment_void where ticket_id = ?", ticket)).isZero();
		assertThat(stock.get(bottle).onHand()).isEqualTo(3);
	}

	@Test
	void anAuditWriteFailureRollsBackAQuickSaleWholly() {
		openShift(cashier);
		var bottle = trackedProduct(5);
		auditWritesNowFail();

		var result = mvc.post()
			.uri("/api/v1/quick-sales")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"lines\":[{\"productId\":\"%s\",\"modifierOptionIds\":[],\"quantity\":1,\"note\":null,\"allergy\":false}],"
				.formatted(bottle) + "\"payment\":{\"method\":\"CARD\"}}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(InstoreTables.count(jdbc, "ticket")).isZero();
		assertThat(PreparationTables.count(jdbc, "board_order")).isZero();
		assertThat(stock.get(bottle).onHand()).isEqualTo(5);
	}

	// helpers

	private MvcTestResult openShift(UUID who) {
		return mvc.post()
			.uri(SHIFTS)
			.with(AuthenticatedAs.user(who, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"openingFloat\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}")
			.exchange();
	}

	private UUID trackedProduct(int quantity) {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, quantity, null);
		return product;
	}

	private UUID sentTicket(UUID product, int quantity) {
		var opened = mvc.post()
			.uri(TICKETS)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange();
		var ticket = UUID.fromString(idOf(opened));
		var added = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":[],\"quantity\":%d,\"note\":null,\"allergy\":false}"
				.formatted(product, quantity))
			.exchange();
		assertThat(added).hasStatus(HttpStatus.CREATED);
		assertThat(mvc.post().uri(TICKETS + "/" + ticket + "/send").with(AuthenticatedAs.user(cashier, Role.CASHIER)).exchange())
			.hasStatusOk();
		return ticket;
	}

	private MvcTestResult pay(UUID ticket, String amount) {
		return mvc.post()
			.uri(TICKETS + "/" + ticket + "/payments")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"CARD\",\"amount\":{\"amount\":\"%s\",\"currency\":\"PEN\"}}".formatted(amount))
			.exchange();
	}

	private static String paymentId(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return assertThat(result).bodyJson().extractingPath("$.payments[0].id").actual().toString();
	}

	private String status(String shift) {
		return jdbc.sql("select status from instore.register_shift where id = ?")
			.param(UUID.fromString(shift))
			.query(String.class)
			.single();
	}

	private void auditWritesNowFail() {
		when(auditLogs.save(any(AuditLog.class))).thenThrow(new RuntimeException("boom"));
	}

	private long count(String sql, Object param) {
		return jdbc.sql(sql).param(param).query(Long.class).single();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

}
