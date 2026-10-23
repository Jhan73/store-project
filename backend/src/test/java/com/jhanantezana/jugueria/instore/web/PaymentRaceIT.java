package com.jhanantezana.jugueria.instore.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.inventory.internal.StockService;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.InventoryTables;

/** The money and stock races of the register, on real threads and real PostgreSQL. */
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentRaceIT {

	static final String TICKETS = "/api/v1/tickets";

	static final String SHIFTS = "/api/v1/shifts";

	static final int THREADS = 6;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	StockService stock;

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void aProduct() {
		juice = InstoreTables.aSizedProduct(jdbc);
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void payingTheFullBalanceAtOnceTakesItOnceAndDeductsTheStockOnce() throws Exception {
		var cashier = UUID.randomUUID();
		openShift(cashier);
		var bottle = trackedProduct(5);
		var ticket = sentTicket(cashier, bottle, 2);

		var results = race(THREADS, i -> () -> pay(ticket, cashier, "10.00"));

		assertThat(results.stream().filter(r -> r.getResponse().getStatus() == 201)).hasSize(1);
		assertThat(results.stream().filter(r -> r.getResponse().getStatus() != 201)).hasSize(THREADS - 1)
			.allSatisfy(r -> assertThat(r.getResponse().getStatus()).isIn(409, 422));
		assertThat(payments(ticket)).isEqualTo(1);
		assertThat(sales(bottle)).isEqualTo(1);
		assertThat(stock.get(bottle).onHand()).isEqualTo(3);
		assertThat(InstoreTables.ticketStatus(jdbc, ticket)).isEqualTo("CLOSED");
	}

	@Test
	void twoPartialPaymentsAtOnceNeverExceedTheTotal() throws Exception {
		var cashier = UUID.randomUUID();
		openShift(cashier);
		var ticket = sentTicket(cashier, juice.productId(), List.of(juice.small(), juice.small()));

		var results = race(THREADS, i -> () -> pay(ticket, cashier, "6.00"));

		// 10.00 owed: one 6.00 fits, no second does.
		assertThat(results.stream().filter(r -> r.getResponse().getStatus() == 201)).hasSize(1);
		assertThat(paid(ticket)).isEqualByComparingTo("6.00");
	}

	@Test
	void twoTicketsClosedOnTheLastUnitAtOnceAreBothRecordedAndStockGoesNegativeOnce() throws Exception {
		var cashier = UUID.randomUUID();
		openShift(cashier);
		var bottle = trackedProduct(1);
		var first = sentTicket(cashier, bottle, 1);
		var second = sentTicket(cashier, bottle, 1);

		var results = race(2, i -> () -> pay(i == 0 ? first : second, cashier, "5.00"));

		assertThat(results.stream().map(r -> r.getResponse().getStatus())).containsExactly(201, 201);
		assertThat(sales(bottle)).isEqualTo(2);
		assertThat(stock.get(bottle).onHand()).isEqualTo(-1);
		assertThat(payments(first) + payments(second)).isEqualTo(2);
	}

	@Test
	void aPaymentRacingTheShiftCloseIsEitherInTheClosedTotalsOrRejected() throws Exception {
		for (var round = 0; round < 8; round++) {
			var cashier = UUID.randomUUID();
			var shift = openShift(cashier);
			var ticket = sentTicket(cashier, juice.productId(), List.of(juice.small(), juice.small()));

			var results = race(2, i -> i == 0 ? () -> pay(ticket, cashier, "5.00", "CASH", "5.00")
					: () -> closeShift(shift, cashier));

			var payment = results.get(0);
			var close = results.get(1);
			assertThat(close).hasStatusOk();
			var recorded = payments(ticket);
			if (payment.getResponse().getStatus() == 201) {
				assertThat(recorded).isEqualTo(1);
			}
			else {
				assertThat(payment.getResponse().getStatus()).isEqualTo(409);
				assertThat(recorded).isZero();
			}
			// The closed shift accounts for exactly the payments that were recorded in it.
			var totals = jdbc.sql("select totals->'cashReceived'->>'amount' from instore.register_shift where id = ?")
				.param(shift)
				.query(String.class)
				.single();
			assertThat(new BigDecimal(totals)).isEqualByComparingTo(recorded == 1 ? "5.00" : "0.00");
			assertThat(jdbc.sql("select expected_amount from instore.register_shift where id = ?")
				.param(shift)
				.query(BigDecimal.class)
				.single()).isEqualByComparingTo(recorded == 1 ? "105.00" : "100.00");
		}
	}

	// helpers

	private UUID openShift(UUID cashier) {
		var result = mvc.post()
			.uri(SHIFTS)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"openingFloat\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}")
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(idOf(result));
	}

	private MvcTestResult closeShift(UUID shift, UUID cashier) {
		return mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"100.00\",\"currency\":\"PEN\"},\"note\":\"race\"}")
			.exchange();
	}

	private UUID trackedProduct(int quantity) {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, quantity, null);
		return product;
	}

	private UUID sentTicket(UUID cashier, UUID product, int quantity) {
		var ticket = openTicket(cashier);
		addLine(ticket, cashier, product, List.of(), quantity);
		send(ticket, cashier);
		return ticket;
	}

	private UUID sentTicket(UUID cashier, UUID product, List<UUID> options) {
		var ticket = openTicket(cashier);
		for (var option : options) {
			addLine(ticket, cashier, product, List.of(option), 1);
		}
		send(ticket, cashier);
		return ticket;
	}

	private UUID openTicket(UUID cashier) {
		var result = mvc.post()
			.uri(TICKETS)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(idOf(result));
	}

	private void addLine(UUID ticket, UUID cashier, UUID product, List<UUID> options, int quantity) {
		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":%s,\"quantity\":%d,\"note\":null,\"allergy\":false}"
				.formatted(product, options.stream().map(o -> "\"" + o + "\"").toList(), quantity))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
	}

	private void send(UUID ticket, UUID cashier) {
		assertThat(mvc.post().uri(TICKETS + "/" + ticket + "/send").with(AuthenticatedAs.user(cashier, Role.CASHIER)).exchange())
			.hasStatusOk();
	}

	private MvcTestResult pay(UUID ticket, UUID cashier, String amount) {
		return pay(ticket, cashier, amount, "CARD", null);
	}

	private MvcTestResult pay(UUID ticket, UUID cashier, String amount, String method, String received) {
		var body = "{\"method\":\"%s\",\"amount\":{\"amount\":\"%s\",\"currency\":\"PEN\"}%s}".formatted(method, amount,
				received == null ? "" : ",\"receivedAmount\":{\"amount\":\"%s\",\"currency\":\"PEN\"}".formatted(received));
		return mvc.post()
			.uri(TICKETS + "/" + ticket + "/payments")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private long payments(UUID ticket) {
		return jdbc.sql("select count(*) from instore.in_store_payment where ticket_id = ?")
			.param(ticket)
			.query(Long.class)
			.single();
	}

	private BigDecimal paid(UUID ticket) {
		return jdbc.sql("select coalesce(sum(paid_amount), 0) from instore.in_store_payment where ticket_id = ?")
			.param(ticket)
			.query(BigDecimal.class)
			.single();
	}

	private long sales(UUID product) {
		return jdbc.sql("select count(*) from inventory.stock_movement where product_id = ? and type = 'SALE'")
			.param(product)
			.query(Long.class)
			.single();
	}

	private List<MvcTestResult> race(int threads, java.util.function.IntFunction<Callable<MvcTestResult>> task)
			throws Exception {
		var ready = new CountDownLatch(threads);
		var go = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(threads)) {
			var futures = new ArrayList<Future<MvcTestResult>>();
			for (var i = 0; i < threads; i++) {
				var call = task.apply(i);
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return call.call();
				}));
			}
			ready.await();
			go.countDown();
			var results = new ArrayList<MvcTestResult>();
			for (var future : futures) {
				results.add(future.get());
			}
			return results;
		}
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

}
