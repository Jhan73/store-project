package com.jhanantezana.jugueria.instore.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.instore.PaymentRecorded;
import com.jhanantezana.jugueria.instore.PaymentVoided;
import com.jhanantezana.jugueria.instore.SaleRecorded;
import com.jhanantezana.jugueria.inventory.internal.StockService;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.InventoryTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class TicketPaymentIT {

	static final String TICKETS = "/api/v1/tickets";

	static final String SHIFTS = "/api/v1/shifts";

	static final String CHANGED_MIND = "{\"reason\":\"Changed mind\"}";

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

	String ticket;

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void anOpenShiftAndATicket() {
		juice = InstoreTables.aSizedProduct(jdbc);
		InstoreTables.aVoidReason(jdbc, "Changed mind");
		InstoreTables.aVoidReason(jdbc, "Other");
		shift = openShift(cashier, "100.00");
		ticket = openTicket();
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void aCashPaymentRecordsTheChangeAndClosesTheTicketWhenItCoversTheTotal() {
		sentLine(juice.small());

		var result = pay(ticket, "CASH", "5.00", "10.00");

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result.getResponse().getHeader("Location")).isEqualTo(TICKETS + "/" + ticket);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
		assertThat(result).bodyJson().extractingPath("$.paid.amount").isEqualTo("5.00");
		assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("0.00");
		assertThat(result).bodyJson().extractingPath("$.payments[0].method").isEqualTo("CASH");
		assertThat(result).bodyJson().extractingPath("$.payments[0].paid.amount").isEqualTo("5.00");
		assertThat(result).bodyJson().extractingPath("$.payments[0].received.amount").isEqualTo("10.00");
		assertThat(result).bodyJson().extractingPath("$.payments[0].change.amount").isEqualTo("5.00");
		assertThat(result).bodyJson().extractingPath("$.payments[0].voided").isEqualTo(false);
		assertThat(InstoreTables.ticketStatus(jdbc, UUID.fromString(ticket))).isEqualTo("CLOSED");
		assertThat(events.stream(PaymentRecorded.class)).singleElement().satisfies(recorded -> {
			assertThat(recorded.ticketId()).isEqualTo(UUID.fromString(ticket));
			assertThat(recorded.shiftId()).isEqualTo(UUID.fromString(shift));
			assertThat(recorded.method()).isEqualTo(PaymentMethod.CASH);
			assertThat(recorded.paid().amount()).isEqualByComparingTo("5.00");
			assertThat(recorded.change().amount()).isEqualByComparingTo("5.00");
			assertThat(recorded.ticketClosed()).isTrue();
		});
		assertThat(events.stream(SaleRecorded.class)).singleElement().satisfies(sale -> {
			assertThat(sale.ticketId()).isEqualTo(UUID.fromString(ticket));
			assertThat(sale.total().amount()).isEqualByComparingTo("5.00");
			assertThat(sale.lines()).hasSize(1);
		});
	}

	@Test
	void severalPaymentsCoverATicketAndOnlyTheLastOneClosesIt() {
		sentLine(juice.small());
		sentLine(juice.large());

		var first = pay(ticket, "WALLET", "6.50", null);

		assertThat(first).hasStatus(HttpStatus.CREATED);
		assertThat(first).bodyJson().extractingPath("$.status").isEqualTo("OPEN");
		assertThat(first).bodyJson().extractingPath("$.paid.amount").isEqualTo("6.50");
		assertThat(first).bodyJson().extractingPath("$.balance.amount").isEqualTo("6.50");
		assertThat(events.stream(SaleRecorded.class)).isEmpty();

		var second = pay(ticket, "CARD", "6.50", null);

		assertThat(second).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
		assertThat(second).bodyJson().extractingPath("$.payments[*].method").asList().containsExactly("WALLET", "CARD");
		assertThat(events.stream(SaleRecorded.class)).hasSize(1);
	}

	@Test
	void everyNonCashMethodIsRecordedWithoutAnAmountReceived() {
		sentLine(juice.small());
		sentLine(juice.small());
		sentLine(juice.small());

		for (var method : new String[] { "CARD", "WALLET", "TRANSFER" }) {
			assertThat(pay(ticket, method, "5.00", null)).hasStatus(HttpStatus.CREATED);
		}

		assertThat(paymentMethods()).containsExactlyInAnyOrder("CARD", "WALLET", "TRANSFER");
	}

	@Test
	void aNonCashPaymentCannotCarryAnAmountReceived() {
		sentLine(juice.small());

		assertProblem(pay(ticket, "CARD", "5.00", "5.00"), HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-payment");
		assertThat(paymentCount()).isZero();
	}

	@Test
	void aCashPaymentNeedsAnAmountReceivedThatCoversIt() {
		sentLine(juice.small());

		assertProblem(pay(ticket, "CASH", "5.00", null), HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-payment");
		assertProblem(pay(ticket, "CASH", "5.00", "4.99"), HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-payment");
		assertThat(pay(ticket, "CASH", "5.00", "5.00")).hasStatus(HttpStatus.CREATED);
		assertThat(jdbc.sql("select change_amount from instore.in_store_payment where ticket_id = ?")
			.param(UUID.fromString(ticket))
			.query(java.math.BigDecimal.class)
			.single()).isEqualByComparingTo("0.00");
	}

	@Test
	void aPaymentCannotExceedWhatIsStillOwed() {
		sentLine(juice.small());
		pay(ticket, "CARD", "2.00", null);

		var result = pay(ticket, "CARD", "3.01", null);

		assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "instore.payment-exceeds-balance");
		assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("3.00");
		assertThat(paymentCount()).isEqualTo(1);
	}

	@Test
	void theAmountMustBePositiveInTheTicketCurrencyAndWithinTheColumn() {
		sentLine(juice.small());

		assertProblem(pay(ticket, "CARD", "0.00", null), HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-amount");
		assertProblem(pay(ticket, "CARD", "-1.00", null), HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-amount");
		assertProblem(pay(ticket, "CARD", "10000000000.00", null), HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-amount");
		assertProblem(pay(ticket, "CASH", "5.00", "10000000000.00"), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-amount");
		var dollars = payRaw(ticket, cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"method\":\"CARD\",\"amount\":{\"amount\":\"5.00\",\"currency\":\"USD\"}}");
		assertProblem(dollars, HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-amount");
		assertThat(paymentCount()).isZero();
	}

	@Test
	void aPaymentNeedsTheCashiersOwnOpenShift() {
		sentLine(juice.small());
		var other = UUID.randomUUID();

		var result = payAs(ticket, other, Role.CASHIER, "CARD", "5.00", null);

		assertProblem(result, HttpStatus.CONFLICT, "instore.no-open-shift");
		assertThat(paymentCount()).isZero();
		assertThat(InstoreTables.ticketStatus(jdbc, UUID.fromString(ticket))).isEqualTo("OPEN");
	}

	@Test
	void aPaymentIsRejectedOnceTheShiftIsClosedOrBeingClosed() {
		sentLine(juice.small());
		sentLine(juice.small());
		var closing = mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"20.00\",\"currency\":\"PEN\"}}")
			.exchange();
		assertProblem(closing, HttpStatus.CONFLICT, "instore.difference-note-required");

		assertProblem(pay(ticket, "CARD", "5.00", null), HttpStatus.CONFLICT, "instore.shift-closing");

		mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"20.00\",\"currency\":\"PEN\"},\"note\":\"short\"}")
			.exchange();
		assertProblem(pay(ticket, "CARD", "5.00", null), HttpStatus.CONFLICT, "instore.no-open-shift");
		assertThat(paymentCount()).isZero();
	}

	@Test
	void aTicketWithUnsentLinesCannotBePaid() {
		sentLine(juice.small());
		addLine(juice.small());

		assertProblem(pay(ticket, "CARD", "5.00", null), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.ticket-has-unsent-lines");
		assertThat(paymentCount()).isZero();
	}

	@Test
	void aTicketWithNothingToChargeCannotBePaid() {
		assertProblem(pay(ticket, "CARD", "1.00", null), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.payment-exceeds-balance");
	}

	@Test
	void aClosedOrUnknownTicketTakesNoPayment() {
		sentLine(juice.small());
		pay(ticket, "CARD", "5.00", null);

		assertProblem(pay(ticket, "CARD", "1.00", null), HttpStatus.CONFLICT, "instore.ticket-closed");
		assertProblem(pay(UUID.randomUUID().toString(), "CARD", "1.00", null), HttpStatus.NOT_FOUND,
				"instore.ticket-not-found");
	}

	@Test
	void onlyCashiersAndAdminsRecordPayments() {
		sentLine(juice.small());

		assertThat(payAs(ticket, UUID.randomUUID(), Role.SERVER, "CARD", "5.00", null)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(payAs(ticket, UUID.randomUUID(), Role.CUSTOMER, "CARD", "5.00", null)).hasStatus(HttpStatus.FORBIDDEN);
		var anonymous = mvc.post()
			.uri(TICKETS + "/" + ticket + "/payments")
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(paymentBody("CARD", "5.00", null, null))
			.exchange();
		assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
		var admin = UUID.randomUUID();
		openShift(admin, "0.00");
		assertThat(payAs(ticket, admin, Role.ADMIN, "CARD", "5.00", null)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void retryingThePaymentReplaysTheFirstAnswerAndRecordsItOnce() {
		sentLine(juice.small());
		sentLine(juice.small());
		var key = UUID.randomUUID();
		var body = paymentBody("CARD", "5.00", null, null);
		var first = payRaw(ticket, cashier, Role.CASHIER, key, body);

		var retry = payRaw(ticket, cashier, Role.CASHIER, key, body);

		assertThat(first).hasStatus(HttpStatus.CREATED);
		assertThat(retry).hasStatus(HttpStatus.CREATED);
		assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(paymentCount()).isEqualTo(1);
		assertThat(events.stream(PaymentRecorded.class)).hasSize(1);
		assertProblem(payRaw(ticket, cashier, Role.CASHIER, key, paymentBody("CARD", "4.00", null, null)),
				HttpStatus.UNPROCESSABLE_CONTENT, "common.idempotency-key-reused");
	}

	@Test
	void recordingRequiresAnIdempotencyKey() {
		sentLine(juice.small());

		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/payments")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content(paymentBody("CARD", "5.00", null, null))
			.exchange();

		assertProblem(result, HttpStatus.BAD_REQUEST, "common.idempotency-key-required");
	}

	@Test
	void aBillSplitByItemsPaysTheChosenLinesAndNobodyPaysThemTwice() {
		var small = sentLine(juice.small());
		var large = sentLine(juice.large());

		var first = payRaw(ticket, cashier, Role.CASHIER, UUID.randomUUID(), paymentBody("CARD", "5.00", null, List.of(small)));

		assertThat(first).hasStatus(HttpStatus.CREATED);
		assertThat(first).bodyJson().extractingPath("$.payments[0].lineIds").asList().containsExactly(small.toString());
		assertThat(first).bodyJson().extractingPath("$.status").isEqualTo("OPEN");
		assertProblem(payRaw(ticket, cashier, Role.CASHIER, UUID.randomUUID(), paymentBody("CARD", "5.00", null, List.of(small))),
				HttpStatus.CONFLICT, "instore.line-already-paid");

		var last = payRaw(ticket, cashier, Role.CASHIER, UUID.randomUUID(), paymentBody("CASH", "8.00", "10.00", List.of(large)));

		assertThat(last).hasStatus(HttpStatus.CREATED);
		assertThat(last).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
	}

	@Test
	void anItemPaymentMustEqualTheChosenLinesAndTheLinesMustBelongToTheTicket() {
		var small = sentLine(juice.small());
		sentLine(juice.large());

		assertProblem(payRaw(ticket, cashier, Role.CASHIER, UUID.randomUUID(), paymentBody("CARD", "4.00", null, List.of(small))),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-payment");
		assertProblem(payRaw(ticket, cashier, Role.CASHIER, UUID.randomUUID(),
				paymentBody("CARD", "5.00", null, List.of(UUID.randomUUID()))), HttpStatus.NOT_FOUND, "instore.line-not-found");
		assertThat(payRaw(ticket, cashier, Role.CASHIER, UUID.randomUUID(), paymentBody("CARD", "5.00", null, List.of())))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(paymentCount()).isZero();
	}

	@Test
	void aVoidedLineCannotBePaidByItems() {
		var gone = sentLine(juice.small());
		sentLine(juice.large());
		mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines/" + gone + "/void")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(CHANGED_MIND)
			.exchange();

		assertProblem(payRaw(ticket, cashier, Role.CASHIER, UUID.randomUUID(), paymentBody("CARD", "5.00", null, List.of(gone))),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-payment");
	}

	@Test
	void closingTheTicketDeductsTheTrackedStockOnceAndOnlyAtTheEnd() {
		var bottle = trackedProduct(5);
		sentLine(bottle, 2);
		sentLine(juice.small());

		pay(ticket, "CARD", "5.00", null);

		assertThat(stock.get(bottle).onHand()).isEqualTo(5);
		assertThat(ledger(bottle)).isEmpty();

		var closing = pay(ticket, "CARD", "10.00", null);

		assertThat(closing).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
		assertThat(stock.get(bottle).onHand()).isEqualTo(3);
		assertThat(ledger(bottle)).containsExactly("SALE/-2/" + ticket);
	}

	@Test
	void aPaymentThatClosesTheTicketIsRejectedWhenThereIsNotEnoughStockAndNothingIsRecorded() {
		var bottle = trackedProduct(1);
		sentLine(bottle, 2);

		var result = pay(ticket, "CARD", "10.00", null);

		assertProblem(result, HttpStatus.CONFLICT, "inventory.insufficient-stock");
		assertThat(paymentCount()).isZero();
		assertThat(InstoreTables.ticketStatus(jdbc, UUID.fromString(ticket))).isEqualTo("OPEN");
		assertThat(stock.get(bottle).onHand()).isEqualTo(1);
		assertThat(events.stream(SaleRecorded.class)).isEmpty();
	}

	@Test
	void voidingAPaymentOnAnOpenTicketReopensTheBalanceAndFreesItsLines() {
		var small = sentLine(juice.small());
		sentLine(juice.large());
		var payment = paymentId(payRaw(ticket, cashier, Role.CASHIER, UUID.randomUUID(),
				paymentBody("CASH", "5.00", "20.00", List.of(small))), 0);

		var result = voidPayment(payment, "{\"reason\":\"Changed mind\",\"note\":\"Wrong method\"}", cashier, Role.CASHIER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.paid.amount").isEqualTo("0.00");
		assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("13.00");
		assertThat(result).bodyJson().extractingPath("$.payments[0].voided").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.payments[0].voidReasonCode").isEqualTo("Changed mind");
		assertThat(result).bodyJson().extractingPath("$.payments[0].voidNote").isEqualTo("Wrong method");
		assertThat(events.stream(PaymentVoided.class)).singleElement().satisfies(voided -> {
			assertThat(voided.paymentId()).isEqualTo(UUID.fromString(payment));
			assertThat(voided.shiftId()).isEqualTo(UUID.fromString(shift));
			assertThat(voided.amount().amount()).isEqualByComparingTo("5.00");
			assertThat(voided.method()).isEqualTo(PaymentMethod.CASH);
		});
		assertThat(payRaw(ticket, cashier, Role.CASHIER, UUID.randomUUID(), paymentBody("CARD", "5.00", null, List.of(small))))
			.hasStatus(HttpStatus.CREATED);
	}

	@Test
	void voidingAPaymentTwiceChangesNothing() {
		sentLine(juice.small());
		sentLine(juice.small());
		var payment = paymentId(pay(ticket, "CARD", "5.00", null), 0);
		voidPayment(payment, CHANGED_MIND, cashier, Role.CASHIER);

		var again = voidPayment(payment, "{\"reason\":\"Other\",\"note\":\"again\"}", cashier, Role.CASHIER);

		assertThat(again).hasStatusOk();
		assertThat(events.stream(PaymentVoided.class)).hasSize(1);
		assertThat(jdbc.sql("select count(*) from instore.payment_void where payment_id = ?")
			.param(UUID.fromString(payment))
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void aPaymentOfAClosedTicketCannotBeVoidedOnItsOwn() {
		sentLine(juice.small());
		var payment = paymentId(pay(ticket, "CARD", "5.00", null), 0);

		assertProblem(voidPayment(payment, CHANGED_MIND, cashier, Role.CASHIER), HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void aPaymentVoidNeedsAnActiveVoidReasonAnExistingPaymentAndTheRightRole() {
		sentLine(juice.small());
		sentLine(juice.small());
		var payment = paymentId(pay(ticket, "CARD", "5.00", null), 0);

		assertProblem(voidPayment(payment, "{\"reason\":\"Nonsense\"}", cashier, Role.CASHIER),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-reason");
		assertProblem(voidPayment(payment, "{\"reason\":\"Other\"}", cashier, Role.CASHIER),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-reason");
		assertProblem(voidPayment(UUID.randomUUID().toString(), CHANGED_MIND, cashier, Role.CASHIER), HttpStatus.NOT_FOUND,
				"instore.payment-not-found");
		assertThat(voidPayment(payment, CHANGED_MIND, UUID.randomUUID(), Role.SERVER)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(voidPayment(payment, "{}", cashier, Role.CASHIER)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(events.stream(PaymentVoided.class)).isEmpty();
	}

	@Test
	void aCashierNeedsAnOpenShiftToVoidAPayment() {
		sentLine(juice.small());
		sentLine(juice.small());
		sentLine(juice.small());
		var first = paymentId(pay(ticket, "CASH", "5.00", "5.00"), 0);

		assertProblem(voidPayment(first, CHANGED_MIND, UUID.randomUUID(), Role.CASHIER), HttpStatus.CONFLICT,
				"instore.no-open-shift");
	}

	@Test
	void aCashierCannotVoidAPaymentTakenInAnotherCashiersShift() {
		sentLine(juice.small());
		sentLine(juice.small());
		var payment = paymentId(pay(ticket, "CASH", "5.00", "5.00"), 0);
		var other = UUID.randomUUID();
		var otherShift = openShift(other, "50.00");

		var result = voidPayment(payment, CHANGED_MIND, other, Role.CASHIER);

		assertProblem(result, HttpStatus.FORBIDDEN, "instore.sale-void-not-permitted");
		assertThat(voidCount(payment)).isZero();
		assertThat(otherShift).isNotEqualTo(shift);
		assertThat(events.stream(PaymentVoided.class)).isEmpty();
	}

	@Test
	void anAdminVoidsIntoTheShiftThePaymentWasTakenInWhileItIsOpen() {
		sentLine(juice.small());
		sentLine(juice.small());
		var payment = paymentId(pay(ticket, "CASH", "5.00", "5.00"), 0);
		var admin = UUID.randomUUID();
		openShift(admin, "10.00");

		var result = voidPayment(payment, CHANGED_MIND, admin, Role.ADMIN);

		assertThat(result).hasStatusOk();
		assertThat(voidShift(payment)).isEqualTo(UUID.fromString(shift));
		var closed = mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}")
			.exchange();
		assertThat(closed).bodyJson().extractingPath("$.expectedCash.amount").isEqualTo("100.00");
		assertThat(closed).bodyJson().extractingPath("$.totals.voidedCash.amount").isEqualTo("5.00");
	}

	@Test
	void anAdminVoidsWithNoDrawerOnceThePaymentsShiftIsClosed() {
		sentLine(juice.small());
		sentLine(juice.small());
		var payment = paymentId(pay(ticket, "CASH", "5.00", "5.00"), 0);
		mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"105.00\",\"currency\":\"PEN\"}}")
			.exchange();
		var admin = UUID.randomUUID();
		openShift(admin, "10.00");

		assertThat(voidPayment(payment, CHANGED_MIND, admin, Role.ADMIN)).hasStatusOk();

		assertThat(voidShift(payment)).isNull();
	}

	@Test
	void aPaymentOfAShiftBeingClosedCannotBeVoided() {
		sentLine(juice.small());
		sentLine(juice.small());
		var payment = paymentId(pay(ticket, "CASH", "5.00", "5.00"), 0);
		mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"20.00\",\"currency\":\"PEN\"}}")
			.exchange();

		assertProblem(voidPayment(payment, CHANGED_MIND, cashier, Role.CASHIER), HttpStatus.CONFLICT, "instore.shift-closing");
		assertProblem(voidPayment(payment, CHANGED_MIND, UUID.randomUUID(), Role.ADMIN), HttpStatus.CONFLICT,
				"instore.shift-closing");
		assertThat(voidCount(payment)).isZero();
	}

	@Test
	void theCashOfAVoidedPaymentLeavesTheDrawerOfTheShiftThatVoidedIt() {
		sentLine(juice.small());
		sentLine(juice.small());
		var cash = paymentId(pay(ticket, "CASH", "5.00", "10.00"), 0);
		pay(ticket, "CARD", "2.00", null);
		voidPayment(cash, CHANGED_MIND, cashier, Role.CASHIER);

		var result = mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}")
			.exchange();

		// float 100 + 10 received - 5 change - 5 voided = 100
		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.expectedCash.amount").isEqualTo("100.00");
		assertThat(result).bodyJson().extractingPath("$.totals.voidedCash.amount").isEqualTo("5.00");
		assertThat(result).bodyJson().extractingPath("$.totals.cashReceived.amount").isEqualTo("10.00");
		assertThat(result).bodyJson().extractingPath("$.totals.changeGiven.amount").isEqualTo("5.00");
		assertThat(result).bodyJson()
			.extractingPath("$.totals.byMethod[?(@.method=='CASH')].net.amount")
			.asList()
			.containsExactly("0.00");
		assertThat(result).bodyJson()
			.extractingPath("$.totals.byMethod[?(@.method=='CARD')].net.amount")
			.asList()
			.containsExactly("2.00");
	}

	@Test
	void theShiftReportShowsCashSalesAndTotalsPerMethodOnlyAfterTheCount() {
		sentLine(juice.small());
		sentLine(juice.large());
		pay(ticket, "CASH", "5.00", "20.00");
		pay(ticket, "CARD", "8.00", null);
		var open = mvc.get().uri(SHIFTS + "/" + shift).with(AuthenticatedAs.user(cashier, Role.CASHIER)).exchange();
		assertThat(open).bodyJson().extractingPath("$.expectedCash").isNull();
		assertThat(open).bodyJson().extractingPath("$.totals").isNull();

		var closed = mvc.post()
			.uri(SHIFTS + "/" + shift + "/close")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"countedCash\":{\"amount\":\"105.00\",\"currency\":\"PEN\"}}")
			.exchange();

		assertThat(closed).bodyJson().extractingPath("$.expectedCash.amount").isEqualTo("105.00");
		assertThat(closed).bodyJson().extractingPath("$.difference.amount").isEqualTo("0.00");
		assertThat(closed).bodyJson()
			.extractingPath("$.totals.byMethod[?(@.method=='CARD')].recorded.amount")
			.asList()
			.containsExactly("8.00");
	}

	@Test
	void aLineCoveredByAPaymentCannotBeVoidedUntilThePaymentIsVoided() {
		var small = sentLine(juice.small());
		sentLine(juice.large());
		var payment = paymentId(payRaw(ticket, cashier, Role.CASHIER, UUID.randomUUID(),
				paymentBody("CARD", "5.00", null, List.of(small))), 0);

		assertProblem(voidLine(small), HttpStatus.CONFLICT, "instore.line-covered-by-payment");
		assertThat(InstoreTables.lineState(jdbc, small)).isEqualTo("SENT");

		voidPayment(payment, CHANGED_MIND, cashier, Role.CASHIER);

		assertThat(voidLine(small)).hasStatusOk();
	}

	@Test
	void aLineCannotBeVoidedWhenThePaymentsWouldExceedWhatIsLeft() {
		var small = sentLine(juice.small());
		var large = sentLine(juice.large());
		pay(ticket, "CARD", "10.00", null);

		assertProblem(voidLine(large), HttpStatus.CONFLICT, "instore.line-covered-by-payment");
		assertThat(InstoreTables.lineState(jdbc, large)).isEqualTo("SENT");
		assertThat(small).isNotNull();
	}

	@Test
	void voidingTheLastUnpaidLineClosesATicketThatIsNowFullyPaid() {
		var small = sentLine(juice.small());
		var large = sentLine(juice.large());
		payRaw(ticket, cashier, Role.CASHIER, UUID.randomUUID(), paymentBody("CARD", "5.00", null, List.of(small)));

		var result = voidLine(large);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
		assertThat(InstoreTables.ticketStatus(jdbc, UUID.fromString(ticket))).isEqualTo("CLOSED");
		assertThat(events.stream(SaleRecorded.class)).singleElement().satisfies(sale -> {
			assertThat(sale.total().amount()).isEqualByComparingTo("5.00");
			assertThat(sale.lines()).hasSize(1);
		});
	}

	@Test
	void splittingTheBalanceEvenlyGivesTheLastPartTheRemainder() {
		for (var i = 0; i < 2; i++) {
			sentLine(juice.small());
		}

		var result = split(ticket, 3);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.parts[*].amount").asList().containsExactly("3.33", "3.33", "3.34");
	}

	@Test
	void theEvenSplitIsOfWhatIsStillOwed() {
		sentLine(juice.small());
		sentLine(juice.large());
		pay(ticket, "CARD", "3.00", null);

		var result = split(ticket, 2);

		assertThat(result).bodyJson().extractingPath("$.parts[*].amount").asList().containsExactly("5.00", "5.00");
	}

	@Test
	void anEvenSplitNeedsAtLeastTwoPartsAndSomethingOwed() {
		sentLine(juice.small());

		assertProblem(split(ticket, 1), HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-payment");
		assertProblem(split(ticket, 21), HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-payment");
		pay(ticket, "CARD", "5.00", null);
		assertProblem(split(ticket, 2), HttpStatus.CONFLICT, "instore.ticket-closed");
		assertThat(split(UUID.randomUUID().toString(), 2)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(splitAs(ticket, Role.SERVER, 2)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void theTicketShowsWhatWasPaidAndWhatIsLeft() {
		sentLine(juice.small());
		sentLine(juice.large());
		pay(ticket, "CARD", "4.00", null);

		var read = mvc.get().uri(TICKETS + "/" + ticket).with(AuthenticatedAs.user(cashier, Role.SERVER)).exchange();

		assertThat(read).bodyJson().extractingPath("$.total.amount").isEqualTo("13.00");
		assertThat(read).bodyJson().extractingPath("$.paid.amount").isEqualTo("4.00");
		assertThat(read).bodyJson().extractingPath("$.balance.amount").isEqualTo("9.00");
		assertThat(read).bodyJson().extractingPath("$.saleVoid").isNull();
		assertThat(read).bodyJson().extractingPath("$.quickSale").isEqualTo(false);
	}

	// helpers

	private String openShift(UUID cashierId, String amount) {
		var result = mvc.post()
			.uri(SHIFTS)
			.with(AuthenticatedAs.user(cashierId, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"openingFloat\":{\"amount\":\"%s\",\"currency\":\"PEN\"}}".formatted(amount))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return idOf(result);
	}

	private String openTicket() {
		var result = mvc.post()
			.uri(TICKETS)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange();
		return idOf(result);
	}

	private UUID addLine(UUID option) {
		return addLine(juice.productId(), List.of(option), 1);
	}

	private UUID addLine(UUID product, List<UUID> options, int quantity) {
		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":%s,\"quantity\":%d,\"note\":null,\"allergy\":false}"
				.formatted(product, options.stream().map(o -> "\"" + o + "\"").toList(), quantity))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(assertThat(result).bodyJson().extractingPath("$.lines[-1:].id").asList().actual().get(0).toString());
	}

	private UUID sentLine(UUID option) {
		var line = addLine(option);
		send();
		return line;
	}

	private UUID sentLine(UUID product, int quantity) {
		var line = addLine(product, List.of(), quantity);
		send();
		return line;
	}

	private void send() {
		assertThat(mvc.post().uri(TICKETS + "/" + ticket + "/send").with(AuthenticatedAs.user(cashier, Role.CASHIER)).exchange())
			.hasStatusOk();
	}

	private UUID trackedProduct(int quantity) {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, quantity, null);
		return product;
	}

	private static String paymentBody(String method, String amount, String received, List<UUID> lineIds) {
		return "{\"method\":\"%s\",\"amount\":{\"amount\":\"%s\",\"currency\":\"PEN\"}%s%s}".formatted(method, amount,
				received == null ? "" : ",\"receivedAmount\":{\"amount\":\"%s\",\"currency\":\"PEN\"}".formatted(received),
				lineIds == null ? "" : ",\"lineIds\":" + lineIds.stream().map(l -> "\"" + l + "\"").toList());
	}

	private MvcTestResult pay(String target, String method, String amount, String received) {
		return payAs(target, cashier, Role.CASHIER, method, amount, received);
	}

	private MvcTestResult payAs(String target, UUID actor, Role role, String method, String amount, String received) {
		return payRaw(target, actor, role, UUID.randomUUID(), paymentBody(method, amount, received, null));
	}

	private MvcTestResult payRaw(String target, UUID actor, Role role, UUID key, String body) {
		return mvc.post()
			.uri(TICKETS + "/" + target + "/payments")
			.with(AuthenticatedAs.user(actor, role))
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private MvcTestResult voidPayment(String payment, String body, UUID actor, Role role) {
		return mvc.post()
			.uri(TICKETS + "/" + ticket + "/payments/" + payment + "/void")
			.with(AuthenticatedAs.user(actor, role))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private MvcTestResult voidLine(UUID line) {
		return mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines/" + line + "/void")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(CHANGED_MIND)
			.exchange();
	}

	private MvcTestResult split(String target, int parts) {
		return mvc.get()
			.uri(TICKETS + "/" + target + "/split?parts=" + parts)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.exchange();
	}

	private MvcTestResult splitAs(String target, Role role, int parts) {
		return mvc.get()
			.uri(TICKETS + "/" + target + "/split?parts=" + parts)
			.with(AuthenticatedAs.user(UUID.randomUUID(), role))
			.exchange();
	}

	private String paymentId(MvcTestResult result, int index) {
		return assertThat(result).bodyJson().extractingPath("$.payments[" + index + "].id").actual().toString();
	}

	private long voidCount(String payment) {
		return jdbc.sql("select count(*) from instore.payment_void where payment_id = ?")
			.param(UUID.fromString(payment))
			.query(Long.class)
			.single();
	}

	private UUID voidShift(String payment) {
		return jdbc.sql("select shift_id from instore.payment_void where payment_id = ?")
			.param(UUID.fromString(payment))
			.query((rs, row) -> rs.getObject("shift_id", UUID.class))
			.list()
			.get(0);
	}

	private long paymentCount() {
		return jdbc.sql("select count(*) from instore.in_store_payment where ticket_id = ?")
			.param(UUID.fromString(ticket))
			.query(Long.class)
			.single();
	}

	private List<String> paymentMethods() {
		return jdbc.sql("select method from instore.in_store_payment where ticket_id = ?")
			.param(UUID.fromString(ticket))
			.query(String.class)
			.list();
	}

	private List<String> ledger(UUID product) {
		return jdbc.sql("""
				select type || '/' || on_hand_delta || '/' || reference_id from inventory.stock_movement
				where product_id = ? and type in ('SALE', 'RETURN') order by occurred_at, id
				""").param(product).query(String.class).list();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

}
