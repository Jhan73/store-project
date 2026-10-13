package com.jhanantezana.jugueria.instore.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

/**
 * The shift row is the register's lock. One transaction holds it, in the way a command or a close would, and the
 * others are shown to wait for it and to see its outcome, with no timing luck involved.
 */
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShiftLockingIT {

	static final String TICKETS = "/api/v1/tickets";

	static final String SHIFTS = "/api/v1/shifts";

	static final long BLOCKED_FOR_SECONDS = 2;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TransactionTemplate transactions;

	ExecutorService pool;

	UUID cashier = UUID.randomUUID();

	UUID shift;

	@BeforeEach
	void anOpenShift() {
		pool = Executors.newFixedThreadPool(6);
		InstoreTables.aVoidReason(jdbc, "Changed mind");
		InstoreTables.aCashOutReason(jdbc, "Supplier");
		shift = UUID.fromString(idOf(mvc.post()
			.uri(SHIFTS)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"openingFloat\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}")
			.exchange()));
	}

	@AfterEach
	void cleanUp() {
		pool.shutdownNow();
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void aCloseWaitsForAPaymentInFlightAndCountsIt() throws Exception {
		var close = transactions.execute(status -> {
			shareShift();
			jdbc.sql("""
					insert into instore.in_store_payment (id, ticket_id, shift_id, method, currency, paid_amount,
						received_amount, change_amount, recorded_by, recorded_at)
					values (?, ?, ?, 'CASH', 'PEN', 5.00, 5.00, 0, ?, now())
					""").param(UUID.randomUUID()).param(UUID.randomUUID()).param(shift).param(cashier).update();
			var started = pool.submit(() -> closeShift("105.00"));
			assertBlocked(started);
			return started;
		});

		var result = close.get(10, TimeUnit.SECONDS);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.expectedCash.amount").isEqualTo("105.00");
		assertThat(result).bodyJson().extractingPath("$.difference.amount").isEqualTo("0.00");
	}

	@Test
	void aCloseWaitsForACashMovementInFlightAndCountsIt() throws Exception {
		var close = transactions.execute(status -> {
			shareShift();
			jdbc.sql("""
					insert into instore.cash_movement (id, shift_id, type, currency, amount, note, recorded_by, recorded_at)
					values (?, ?, 'IN', 'PEN', 20.00, 'Change', ?, now())
					""").param(UUID.randomUUID()).param(shift).param(cashier).update();
			var started = pool.submit(() -> closeShift("120.00"));
			assertBlocked(started);
			return started;
		});

		var result = close.get(10, TimeUnit.SECONDS);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.expectedCash.amount").isEqualTo("120.00");
		assertThat(result).bodyJson().extractingPath("$.totals.cashIn.amount").isEqualTo("20.00");
	}

	@Test
	void everyCommandWaitsForAClosingShiftAndThenSeesItClosed() throws Exception {
		var open = ticketWithLines(3);
		var openPayment = paymentId(pay(open, "CARD", "5.00"));
		var paid = ticketWithLines(1);
		var alsoPaid = ticketWithLines(1);
		pay(alsoPaid, "CARD", "5.00");
		pay(paid, "CARD", "5.00");

		var results = transactions.execute(status -> {
			jdbc.sql("select id from instore.register_shift where id = ? for update").param(shift).query().listOfRows();
			jdbc.sql("""
					update instore.register_shift set status = 'CLOSED', close_kind = 'FORCED', force_reason = 'test',
						expected_amount = 100.00, closed_by = ?, closed_at = now(), totals = '{}'::jsonb where id = ?
					""").param(cashier).param(shift).update();
			var tasks = new Future<?>[] { pool.submit(() -> pay(open, "CARD", "5.00")),
					pool.submit(() -> voidPayment(open, openPayment, cashier, Role.CASHIER)),
					pool.submit(() -> movement()), pool.submit(() -> voidSale(alsoPaid, cashier, Role.CASHIER)),
					pool.submit(() -> voidSale(paid, UUID.randomUUID(), Role.ADMIN)) };
			for (var task : tasks) {
				assertBlocked(task);
			}
			return tasks;
		});

		assertProblem((MvcTestResult) results[0].get(10, TimeUnit.SECONDS), HttpStatus.CONFLICT, "instore.no-open-shift");
		assertProblem((MvcTestResult) results[1].get(10, TimeUnit.SECONDS), HttpStatus.CONFLICT, "instore.no-open-shift");
		assertProblem((MvcTestResult) results[2].get(10, TimeUnit.SECONDS), HttpStatus.CONFLICT, "instore.shift-closed");
		assertProblem((MvcTestResult) results[3].get(10, TimeUnit.SECONDS), HttpStatus.CONFLICT, "instore.no-open-shift");
		// The ADMIN's void waited, found the shift closed and so has no drawer behind it.
		var admin = (MvcTestResult) results[4].get(10, TimeUnit.SECONDS);
		assertThat(admin).hasStatusOk();
		assertThat(jdbc.sql("select count(*) from instore.payment_void where ticket_id = ? and shift_id is null")
			.param(paid)
			.query(Long.class)
			.single()).isEqualTo(1);
		assertThat(jdbc.sql("select count(*) from instore.in_store_payment where ticket_id = ?")
			.param(open)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	// helpers

	private void shareShift() {
		jdbc.sql("select id from instore.register_shift where id = ? for share").param(shift).query().listOfRows();
	}

	private static void assertBlocked(Future<?> task) {
		assertThatThrownBy(() -> task.get(BLOCKED_FOR_SECONDS, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
	}

	private MvcTestResult closeShift(String counted) {
		return mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"%s\",\"currency\":\"PEN\"}}".formatted(counted))
			.exchange();
	}

	private MvcTestResult movement() {
		return mvc.post()
			.uri(SHIFTS + "/" + shift + "/cash-movements")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"IN\",\"amount\":{\"amount\":\"1.00\",\"currency\":\"PEN\"},\"note\":\"x\"}")
			.exchange();
	}

	private UUID ticketWithLines(int small) {
		var juice = InstoreTables.aSizedProduct(jdbc);
		var id = UUID.fromString(idOf(mvc.post()
			.uri(TICKETS)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange()));
		for (var i = 0; i < small; i++) {
			assertThat(mvc.post()
				.uri(TICKETS + "/" + id + "/lines")
				.with(AuthenticatedAs.user(cashier, Role.CASHIER))
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":\"%s\",\"modifierOptionIds\":[\"%s\"],\"quantity\":1,\"note\":null,\"allergy\":false}"
					.formatted(juice.productId(), juice.small()))
				.exchange()).hasStatus(HttpStatus.CREATED);
		}
		assertThat(mvc.post().uri(TICKETS + "/" + id + "/send").with(AuthenticatedAs.user(cashier, Role.CASHIER)).exchange())
			.hasStatusOk();
		return id;
	}

	private MvcTestResult pay(UUID target, String method, String amount) {
		return mvc.post()
			.uri(TICKETS + "/" + target + "/payments")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"%s\",\"amount\":{\"amount\":\"%s\",\"currency\":\"PEN\"}}".formatted(method, amount))
			.exchange();
	}

	private MvcTestResult voidPayment(UUID target, String payment, UUID who, Role role) {
		return mvc.post()
			.uri(TICKETS + "/" + target + "/payments/" + payment + "/void")
			.with(AuthenticatedAs.user(who, role))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();
	}

	private MvcTestResult voidSale(UUID target, UUID who, Role role) {
		return mvc.post()
			.uri(TICKETS + "/" + target + "/void")
			.with(AuthenticatedAs.user(who, role))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();
	}

	private static String paymentId(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return assertThat(result).bodyJson().extractingPath("$.payments[0].id").actual().toString();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

}
