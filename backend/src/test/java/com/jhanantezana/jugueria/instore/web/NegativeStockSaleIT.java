package com.jhanantezana.jugueria.instore.web;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.inventory.StockDeficit;
import com.jhanantezana.jugueria.inventory.internal.StockService;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.InventoryTables;

/** Money already taken is never rejected: a sale the shelf cannot cover closes and stock goes negative. */
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class NegativeStockSaleIT {

	static final String TICKETS = "/api/v1/tickets";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	StockService stock;

	@Autowired
	ApplicationEvents events;

	UUID cashier = UUID.randomUUID();

	@BeforeEach
	void anOpenShift() {
		InstoreTables.aVoidReason(jdbc, "Changed mind");
		storeSettingIs(false);
		assertThat(mvc.post()
			.uri("/api/v1/shifts")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"openingFloat\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}")
			.exchange()).hasStatus(HttpStatus.CREATED);
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void aShortSaleClosesWhateverTheOldSettingSays() {
		var bottle = trackedProduct(1);
		var ticket = sentTicket(bottle, 2);

		var result = pay(ticket, UUID.randomUUID());

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
		assertThat(stock.get(bottle).onHand()).isEqualTo(-1);
		assertThat(ledger(bottle)).containsExactly("SALE/-2/" + ticket);
		assertThat(InventoryTables.isAvailable(jdbc, bottle)).isFalse();
		assertThat(jdbc.sql("select count(*) from instore.in_store_payment where ticket_id = ?")
			.param(ticket)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void aShortSaleRaisesAStockDeficitAlert() {
		var bottle = trackedProduct(1);
		var ticket = sentTicket(bottle, 3);

		pay(ticket, UUID.randomUUID());

		assertThat(events.stream(StockDeficit.class)).singleElement().satisfies(deficit -> {
			assertThat(deficit.productId()).isEqualTo(bottle);
			assertThat(deficit.referenceId()).isEqualTo(ticket);
			assertThat(deficit.shortBy()).isEqualTo(2);
			assertThat(deficit.onHand()).isEqualTo(-2);
		});
		assertThat(jdbc.sql("select count(*) from audit.audit_log where action = 'STOCK_DEFICIT' and entity_id = ?")
			.param(bottle)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void retryingTheClosingPaymentDoesNotDeductTwice() {
		var bottle = trackedProduct(1);
		var ticket = sentTicket(bottle, 2);
		var key = UUID.randomUUID();

		pay(ticket, key);
		var retry = pay(ticket, key);

		assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(stock.get(bottle).onHand()).isEqualTo(-1);
		assertThat(ledger(bottle)).hasSize(1);
	}

	@Test
	void voidingTheSaleReturnsTheUnitsAndBringsStockBackUp() {
		var bottle = trackedProduct(1);
		var ticket = sentTicket(bottle, 2);
		pay(ticket, UUID.randomUUID());

		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/void")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(stock.get(bottle).onHand()).isEqualTo(1);
		assertThat(ledger(bottle)).containsExactly("SALE/-2/" + ticket, "RETURN/2/" + ticket);
	}

	@Test
	void aLineVoidThatSettlesTheTicketClosesItBelowZeroToo() {
		var bottle = trackedProduct(1);
		var ticket = UUID.fromString(openTicket());
		var bottles = addLine(ticket, bottle, 2);
		var extra = addLine(ticket, bottle, 1);
		send(ticket);
		var paid = mvc.post()
			.uri(TICKETS + "/" + ticket + "/payments")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"CARD\",\"amount\":{\"amount\":\"10.00\",\"currency\":\"PEN\"},\"lineIds\":[\"%s\"]}"
				.formatted(bottles))
			.exchange();
		assertThat(paid).hasStatus(HttpStatus.CREATED);

		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines/" + extra + "/void")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
		assertThat(stock.get(bottle).onHand()).isEqualTo(-1);
	}

	@Test
	void aQuickSaleOfTheLastUnitsAlsoClosesBelowZero() {
		var bottle = trackedProduct(1);

		var result = mvc.post()
			.uri("/api/v1/quick-sales")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"lines\":[{\"productId\":\"%s\",\"modifierOptionIds\":[],\"quantity\":3,\"note\":null,\"allergy\":false}],"
				.formatted(bottle) + "\"payment\":{\"method\":\"CARD\"}}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(stock.get(bottle).onHand()).isEqualTo(-2);
	}

	@Test
	void twoTicketsClosingOnTheLastUnitAtOnceBothSucceed() throws Exception {
		var bottle = trackedProduct(1);
		var first = sentTicket(bottle, 1);
		var second = sentTicket(bottle, 1);

		var results = race(2, i -> () -> pay(i == 0 ? first : second, UUID.randomUUID()));

		assertThat(results).allSatisfy(r -> assertThat(r).hasStatus(HttpStatus.CREATED));
		assertThat(stock.get(bottle).onHand()).isEqualTo(-1);
		assertThat(ledger(bottle)).hasSize(2);
	}

	// helpers

	private void storeSettingIs(boolean allowed) {
		jdbc.sql("update store.store_settings set allow_negative_stock_on_sale = ?").param(allowed).update();
	}

	private UUID trackedProduct(int quantity) {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, quantity, null);
		return product;
	}

	private String openTicket() {
		var result = mvc.post()
			.uri(TICKETS)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange();
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private UUID addLine(UUID ticket, UUID product, int quantity) {
		var added = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":[],\"quantity\":%d,\"note\":null,\"allergy\":false}"
				.formatted(product, quantity))
			.exchange();
		assertThat(added).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(assertThat(added).bodyJson().extractingPath("$.lines[-1:].id").asList().actual().get(0).toString());
	}

	private void send(UUID ticket) {
		assertThat(mvc.post().uri(TICKETS + "/" + ticket + "/send").with(AuthenticatedAs.user(cashier, Role.CASHIER)).exchange())
			.hasStatusOk();
	}

	private UUID sentTicket(UUID product, int quantity) {
		var ticket = UUID.fromString(openTicket());
		addLine(ticket, product, quantity);
		send(ticket);
		return ticket;
	}

	private MvcTestResult pay(UUID ticket, UUID key) {
		var total = jdbc.sql("select sum(unit_price_amount * quantity) from instore.ticket_line where ticket_id = ?")
			.param(ticket)
			.query(java.math.BigDecimal.class)
			.single();
		return mvc.post()
			.uri(TICKETS + "/" + ticket + "/payments")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"CARD\",\"amount\":{\"amount\":\"%s\",\"currency\":\"PEN\"}}".formatted(total.toPlainString()))
			.exchange();
	}

	private List<String> ledger(UUID product) {
		return jdbc.sql("""
				select type || '/' || on_hand_delta || '/' || reference_id from inventory.stock_movement
				where product_id = ? and type in ('SALE', 'RETURN') order by occurred_at, id
				""").param(product).query(String.class).list();
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

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

}
