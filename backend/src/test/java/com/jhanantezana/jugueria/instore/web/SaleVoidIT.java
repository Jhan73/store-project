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
import com.jhanantezana.jugueria.instore.PaymentVoided;
import com.jhanantezana.jugueria.instore.SaleVoided;
import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.inventory.internal.StockService;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.InventoryTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class SaleVoidIT {

	static final String TICKETS = "/api/v1/tickets";

	static final String SHIFTS = "/api/v1/shifts";

	static final String WRONG_ORDER = "{\"reason\":\"Changed mind\",\"note\":\"Rang the wrong order\"}";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@Autowired
	StockService stock;

	UUID cashier = UUID.randomUUID();

	String shift;

	UUID bottle;

	UUID ticket;

	@BeforeEach
	void aPaidSale() {
		InstoreTables.aVoidReason(jdbc, "Changed mind");
		InstoreTables.aVoidReason(jdbc, "Other");
		shift = openShift(cashier, Role.CASHIER);
		bottle = InventoryTables.aProduct(jdbc);
		stock.track(bottle, true, 0);
		stock.recordEntry(bottle, 10, null);
		ticket = paidTicket(cashier, 2);
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void aCashierVoidsASaleOfTheirOwnOpenShiftAndItsPaymentsAreReversed() {
		var result = voidSale(ticket, WRONG_ORDER, cashier, Role.CASHIER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
		assertThat(result).bodyJson().extractingPath("$.saleVoid.reasonCode").isEqualTo("Changed mind");
		assertThat(result).bodyJson().extractingPath("$.saleVoid.note").isEqualTo("Rang the wrong order");
		assertThat(result).bodyJson().extractingPath("$.saleVoid.voidedBy").isEqualTo(cashier.toString());
		assertThat(result).bodyJson().extractingPath("$.payments[*].voided").asList().containsExactly(true, true);
		assertThat(result).bodyJson().extractingPath("$.paid.amount").isEqualTo("0.00");
		assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("0.00");
		assertThat(events.stream(SaleVoided.class)).singleElement().satisfies(voided -> {
			assertThat(voided.ticketId()).isEqualTo(ticket);
			assertThat(voided.ticketType()).isEqualTo(TicketType.COUNTER);
			assertThat(voided.quickSale()).isFalse();
			assertThat(voided.total().amount()).isEqualByComparingTo("10.00");
			assertThat(voided.lines()).hasSize(1);
			assertThat(voided.reasonCode()).isEqualTo("Changed mind");
		});
		assertThat(events.stream(PaymentVoided.class)).hasSize(2).allSatisfy(voided -> {
			assertThat(voided.saleVoidId()).isNotNull();
			assertThat(voided.shiftId()).isEqualTo(UUID.fromString(shift));
		});
	}

	@Test
	void voidingASaleReturnsTheTrackedStockOnce() {
		assertThat(stock.get(bottle).onHand()).isEqualTo(8);

		voidSale(ticket, WRONG_ORDER, cashier, Role.CASHIER);
		voidSale(ticket, WRONG_ORDER, cashier, Role.CASHIER);

		assertThat(stock.get(bottle).onHand()).isEqualTo(10);
		assertThat(ledger()).containsExactly("SALE/-2/" + ticket, "RETURN/2/" + ticket);
	}

	@Test
	void linesVoidedBeforeTheSaleClosedAreNotReturnedBecauseTheyWereNeverTaken() {
		var other = openTicket(cashier);
		var kept = addAndSend(other, cashier, 1);
		var dropped = addAndSend(other, cashier, 3);
		voidLine(other, dropped, cashier);
		pay(other, cashier, "CARD", "5.00");
		assertThat(stock.get(bottle).onHand()).isEqualTo(7);

		voidSale(other, WRONG_ORDER, cashier, Role.CASHIER);

		assertThat(stock.get(bottle).onHand()).isEqualTo(8);
		assertThat(InstoreTables.lineState(jdbc, kept)).isEqualTo("SENT");
		assertThat(InstoreTables.lineState(jdbc, dropped)).isEqualTo("VOIDED");
	}

	@Test
	void voidingASaleTwiceChangesNothing() {
		voidSale(ticket, WRONG_ORDER, cashier, Role.CASHIER);

		var again = voidSale(ticket, "{\"reason\":\"Other\",\"note\":\"again\"}", cashier, Role.CASHIER);

		assertThat(again).hasStatusOk();
		assertThat(events.stream(SaleVoided.class)).hasSize(1);
		assertThat(events.stream(PaymentVoided.class)).hasSize(2);
		assertThat(jdbc.sql("select count(*) from instore.sale_void where ticket_id = ?")
			.param(ticket)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void theCashLeavesTheDrawerOfTheShiftThatVoidedTheSale() {
		voidSale(ticket, WRONG_ORDER, cashier, Role.CASHIER);

		var closed = mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}")
			.exchange();

		// float 100 + 10 received - 5 change - 5 voided cash
		assertThat(closed).bodyJson().extractingPath("$.expectedCash.amount").isEqualTo("100.00");
		assertThat(closed).bodyJson().extractingPath("$.totals.voidedCash.amount").isEqualTo("5.00");
		assertThat(closed).bodyJson()
			.extractingPath("$.totals.byMethod[?(@.method=='CARD')].net.amount")
			.asList()
			.containsExactly("0.00");
	}

	@Test
	void aCashierCannotVoidTheSaleOfAnotherShift() {
		var other = UUID.randomUUID();
		openShift(other, Role.CASHIER);

		var result = voidSale(ticket, WRONG_ORDER, other, Role.CASHIER);

		assertProblem(result, HttpStatus.FORBIDDEN, "instore.sale-void-not-permitted");
		assertThat(saleVoids()).isZero();
	}

	@Test
	void aCashierCannotVoidASaleOnceTheirShiftIsClosed() {
		mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"105.00\",\"currency\":\"PEN\"}}")
			.exchange();

		assertProblem(voidSale(ticket, WRONG_ORDER, cashier, Role.CASHIER), HttpStatus.CONFLICT, "instore.no-open-shift");
		openShift(cashier, Role.CASHIER);
		assertProblem(voidSale(ticket, WRONG_ORDER, cashier, Role.CASHIER), HttpStatus.FORBIDDEN,
				"instore.sale-void-not-permitted");
		assertThat(saleVoids()).isZero();
	}

	@Test
	void anAdminVoidsWithNoDrawerOnceTheShiftThatTookThePaymentsIsClosed() {
		closeCashiersShift();

		var result = voidSale(ticket, WRONG_ORDER, UUID.randomUUID(), Role.ADMIN);

		assertThat(result).hasStatusOk();
		assertThat(jdbc.sql("select count(*) from instore.payment_void where ticket_id = ? and shift_id is null")
			.param(ticket)
			.query(Long.class)
			.single()).isEqualTo(2);
	}

	@Test
	void anAdminNeverChargesTheirOwnDrawerForAnotherCashiersSale() {
		var admin = UUID.randomUUID();
		var adminShift = openShift(admin, Role.ADMIN);

		voidSale(ticket, WRONG_ORDER, admin, Role.ADMIN);

		assertThat(jdbc.sql("select count(*) from instore.payment_void where ticket_id = ? and shift_id = ?")
			.param(ticket)
			.param(UUID.fromString(shift))
			.query(Long.class)
			.single()).isEqualTo(2);
		assertThat(jdbc.sql("select count(*) from instore.payment_void where shift_id = ?")
			.param(UUID.fromString(adminShift))
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	void aSaleOfAShiftBeingClosedCannotBeVoided() {
		mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"20.00\",\"currency\":\"PEN\"}}")
			.exchange();

		assertProblem(voidSale(ticket, WRONG_ORDER, cashier, Role.CASHIER), HttpStatus.CONFLICT, "instore.shift-closing");
		assertProblem(voidSale(ticket, WRONG_ORDER, UUID.randomUUID(), Role.ADMIN), HttpStatus.CONFLICT,
				"instore.shift-closing");
		assertThat(saleVoids()).isZero();
	}

	@Test
	void aSaleStillOpenCannotBeVoidedAndAnUnknownOneIsNotFound() {
		var open = openTicket(cashier);

		assertProblem(voidSale(open, WRONG_ORDER, cashier, Role.CASHIER), HttpStatus.CONFLICT, "instore.ticket-not-closed");
		assertProblem(voidSale(UUID.randomUUID(), WRONG_ORDER, cashier, Role.CASHIER), HttpStatus.NOT_FOUND,
				"instore.ticket-not-found");
	}

	@Test
	void aSaleVoidNeedsAnActiveVoidReasonAndTheReasonOtherNeedsANote() {
		assertProblem(voidSale(ticket, "{\"reason\":\"Nonsense\"}", cashier, Role.CASHIER), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-reason");
		assertProblem(voidSale(ticket, "{\"reason\":\"Other\"}", cashier, Role.CASHIER), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-reason");
		assertThat(voidSale(ticket, "{}", cashier, Role.CASHIER)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(saleVoids()).isZero();
	}

	@Test
	void onlyCashiersAndAdminsVoidASale() {
		assertThat(voidSale(ticket, WRONG_ORDER, UUID.randomUUID(), Role.SERVER)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(voidSale(ticket, WRONG_ORDER, UUID.randomUUID(), Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
		var anonymous = mvc.post()
			.uri(TICKETS + "/" + ticket + "/void")
			.contentType(MediaType.APPLICATION_JSON)
			.content(WRONG_ORDER)
			.exchange();
		assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void aVoidedSaleTakesNoMoreCommands() {
		voidSale(ticket, WRONG_ORDER, cashier, Role.CASHIER);

		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/payments")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"CARD\",\"amount\":{\"amount\":\"1.00\",\"currency\":\"PEN\"}}")
			.exchange();

		assertProblem(result, HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void manyAdminsVoidingTheSameSaleAtOnceVoidItOnce() throws Exception {
		var results = race(6, i -> () -> voidSale(ticket, WRONG_ORDER, UUID.randomUUID(), Role.ADMIN));

		assertThat(results).allSatisfy(r -> assertThat(r).hasStatusOk());
		assertThat(saleVoids()).isEqualTo(1);
		assertThat(stock.get(bottle).onHand()).isEqualTo(10);
		assertThat(ledger()).containsExactly("SALE/-2/" + ticket, "RETURN/2/" + ticket);
	}

	// helpers

	private void closeCashiersShift() {
		assertThat(mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"105.00\",\"currency\":\"PEN\"}}")
			.exchange()).hasStatusOk();
	}

	private String openShift(UUID who, Role role) {
		var result = mvc.post()
			.uri(SHIFTS)
			.with(AuthenticatedAs.user(who, role))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"openingFloat\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}")
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return idOf(result);
	}

	private UUID openTicket(UUID who) {
		var result = mvc.post()
			.uri(TICKETS)
			.with(AuthenticatedAs.user(who, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange();
		return UUID.fromString(idOf(result));
	}

	private UUID addAndSend(UUID target, UUID who, int quantity) {
		var added = mvc.post()
			.uri(TICKETS + "/" + target + "/lines")
			.with(AuthenticatedAs.user(who, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":[],\"quantity\":%d,\"note\":null,\"allergy\":false}"
				.formatted(bottle, quantity))
			.exchange();
		assertThat(added).hasStatus(HttpStatus.CREATED);
		assertThat(mvc.post().uri(TICKETS + "/" + target + "/send").with(AuthenticatedAs.user(who, Role.CASHIER)).exchange())
			.hasStatusOk();
		return UUID.fromString(assertThat(added).bodyJson().extractingPath("$.lines[-1:].id").asList().actual().get(0).toString());
	}

	private void voidLine(UUID target, UUID line, UUID who) {
		assertThat(mvc.post()
			.uri(TICKETS + "/" + target + "/lines/" + line + "/void")
			.with(AuthenticatedAs.user(who, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange()).hasStatusOk();
	}

	private void pay(UUID target, UUID who, String method, String amount) {
		var body = "{\"method\":\"%s\",\"amount\":{\"amount\":\"%s\",\"currency\":\"PEN\"}}".formatted(method, amount);
		assertThat(mvc.post()
			.uri(TICKETS + "/" + target + "/payments")
			.with(AuthenticatedAs.user(who, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange()).hasStatus(HttpStatus.CREATED);
	}

	// Two units of a 5.00 bottle, paid half in cash with change and half by card.
	private UUID paidTicket(UUID who, int quantity) {
		var target = openTicket(who);
		addAndSend(target, who, quantity);
		var cash = "{\"method\":\"CASH\",\"amount\":{\"amount\":\"5.00\",\"currency\":\"PEN\"},"
				+ "\"receivedAmount\":{\"amount\":\"10.00\",\"currency\":\"PEN\"}}";
		assertThat(mvc.post()
			.uri(TICKETS + "/" + target + "/payments")
			.with(AuthenticatedAs.user(who, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(cash)
			.exchange()).hasStatus(HttpStatus.CREATED);
		pay(target, who, "CARD", "5.00");
		return target;
	}

	private MvcTestResult voidSale(UUID target, String body, UUID who, Role role) {
		return mvc.post()
			.uri(TICKETS + "/" + target + "/void")
			.with(AuthenticatedAs.user(who, role))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private long saleVoids() {
		return jdbc.sql("select count(*) from instore.sale_void where ticket_id = ?")
			.param(ticket)
			.query(Long.class)
			.single();
	}

	private List<String> ledger() {
		return jdbc.sql("""
				select type || '/' || on_hand_delta || '/' || reference_id from inventory.stock_movement
				where product_id = ? and type in ('SALE', 'RETURN') order by occurred_at, id
				""").param(bottle).query(String.class).list();
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

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

}
