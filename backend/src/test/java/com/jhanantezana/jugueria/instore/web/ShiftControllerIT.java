package com.jhanantezana.jugueria.instore.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import com.jhanantezana.jugueria.instore.ShiftClosed;
import com.jhanantezana.jugueria.instore.ShiftOpened;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ShiftControllerIT {

	static final String SHIFTS = "/api/v1/shifts";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	UUID cashier = UUID.randomUUID();

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void aCashierOpensAShiftDeclaringTheFloat() {
		var result = open(cashier, Role.CASHIER, "100.00", UUID.randomUUID());

		assertThat(result).hasStatus(HttpStatus.CREATED);
		var id = idOf(result);
		assertThat(result.getResponse().getHeader("Location")).isEqualTo(SHIFTS + "/" + id);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("OPEN");
		assertThat(result).bodyJson().extractingPath("$.cashierId").isEqualTo(cashier.toString());
		assertThat(result).bodyJson().extractingPath("$.openingFloat.amount").isEqualTo("100.00");
		assertThat(result).bodyJson().extractingPath("$.openingFloat.currency").isEqualTo("PEN");
		assertThat(events.stream(ShiftOpened.class)).singleElement().satisfies(opened -> {
			assertThat(opened.shiftId()).isEqualTo(UUID.fromString(id));
			assertThat(opened.cashierId()).isEqualTo(cashier);
			assertThat(opened.openingFloat().amount()).isEqualByComparingTo("100.00");
		});
	}

	@Test
	void anAdminMayOpenAShiftToo() {
		assertThat(open(UUID.randomUUID(), Role.ADMIN, "0.00", UUID.randomUUID())).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void aCashierHasOnlyOneOpenShift() {
		open(cashier, Role.CASHIER, "100.00", UUID.randomUUID());

		var second = open(cashier, Role.CASHIER, "50.00", UUID.randomUUID());

		assertProblem(second, HttpStatus.CONFLICT, "instore.shift-already-open");
		assertThat(InstoreTables.count(jdbc, "register_shift")).isEqualTo(1);
	}

	@Test
	void eachCashierHasTheirOwnShift() {
		assertThat(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID())).hasStatus(HttpStatus.CREATED);
		assertThat(open(UUID.randomUUID(), Role.CASHIER, "100.00", UUID.randomUUID())).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void retryingTheSameOpenReplaysTheFirstAnswer() {
		var key = UUID.randomUUID();
		var first = open(cashier, Role.CASHIER, "100.00", key);

		var retry = open(cashier, Role.CASHIER, "100.00", key);

		assertThat(retry).hasStatus(HttpStatus.CREATED);
		assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(idOf(retry)).isEqualTo(idOf(first));
		assertThat(events.stream(ShiftOpened.class)).hasSize(1);
	}

	@Test
	void openingRequiresAnIdempotencyKey() {
		var result = mvc.post()
			.uri(SHIFTS)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content(openBody("100.00"))
			.exchange();

		assertProblem(result, HttpStatus.BAD_REQUEST, "common.idempotency-key-required");
	}

	@Test
	void theFloatCannotBeNegativeOrInAnotherCurrency() {
		assertProblem(open(cashier, Role.CASHIER, "-1.00", UUID.randomUUID()), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-amount");
		var dollars = mvc.post()
			.uri(SHIFTS)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"openingFloat\":{\"amount\":\"10.00\",\"currency\":\"USD\"}}")
			.exchange();
		assertProblem(dollars, HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-amount");
		assertProblem(open(cashier, Role.CASHIER, "10000000000.00", UUID.randomUUID()), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-amount");
		assertThat(InstoreTables.count(jdbc, "register_shift")).isZero();
	}

	@Test
	void onlyCashiersAndAdminsMayOpenAShift() {
		assertThat(open(UUID.randomUUID(), Role.SERVER, "10.00", UUID.randomUUID())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(open(UUID.randomUUID(), Role.CUSTOMER, "10.00", UUID.randomUUID())).hasStatus(HttpStatus.FORBIDDEN);
		var anonymous = mvc.post()
			.uri(SHIFTS)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(openBody("10.00"))
			.exchange();
		assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void theCurrentShiftIsTheCallersOwnOpenShift() {
		var id = idOf(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID()));
		open(UUID.randomUUID(), Role.CASHIER, "5.00", UUID.randomUUID());

		var result = get(SHIFTS + "/current", cashier, Role.CASHIER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(id);
	}

	@Test
	void thereIsNoCurrentShiftBeforeOneIsOpened() {
		assertProblem(get(SHIFTS + "/current", cashier, Role.CASHIER), HttpStatus.NOT_FOUND, "instore.shift-not-found");
	}

	@Test
	void anOpenShiftNeverShowsExpectedCashOrTotals() {
		var id = idOf(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID()));
		movement(id, cashier, "{\"type\":\"IN\",\"amount\":{\"amount\":\"30.00\",\"currency\":\"PEN\"},\"note\":\"Change\"}");

		var result = get(SHIFTS + "/" + id, cashier, Role.CASHIER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.expectedCash").isNull();
		assertThat(result).bodyJson().extractingPath("$.difference").isNull();
		assertThat(result).bodyJson().extractingPath("$.totals").isNull();
		assertThat(result).bodyJson().extractingPath("$.countedCash").isNull();
	}

	@Test
	void aCashierCannotReadSomeoneElsesShiftButAnAdminCan() {
		var id = idOf(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID()));

		assertProblem(get(SHIFTS + "/" + id, UUID.randomUUID(), Role.CASHIER), HttpStatus.NOT_FOUND,
				"instore.shift-not-found");
		assertThat(get(SHIFTS + "/" + id, UUID.randomUUID(), Role.ADMIN)).hasStatusOk();
		assertProblem(get(SHIFTS + "/" + UUID.randomUUID(), UUID.randomUUID(), Role.ADMIN), HttpStatus.NOT_FOUND,
				"instore.shift-not-found");
	}

	@Test
	void closingRevealsExpectedCashAndTheDifferenceOnlyAfterTheCount() {
		var id = idOf(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID()));
		movement(id, cashier, "{\"type\":\"IN\",\"amount\":{\"amount\":\"30.00\",\"currency\":\"PEN\"},\"note\":\"Change\"}");
		movement(id, cashier, "{\"type\":\"OUT\",\"amount\":{\"amount\":\"50.00\",\"currency\":\"PEN\"},\"reasonCode\":\"Supplier\"}");

		var result = close(id, cashier, "{\"countedCash\":{\"amount\":\"85.00\",\"currency\":\"PEN\"}}");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
		assertThat(result).bodyJson().extractingPath("$.closeKind").isEqualTo("COUNTED");
		assertThat(result).bodyJson().extractingPath("$.countedCash.amount").isEqualTo("85.00");
		assertThat(result).bodyJson().extractingPath("$.expectedCash.amount").isEqualTo("80.00");
		assertThat(result).bodyJson().extractingPath("$.difference.amount").isEqualTo("5.00");
		assertThat(result).bodyJson().extractingPath("$.totals.cashIn.amount").isEqualTo("30.00");
		assertThat(result).bodyJson().extractingPath("$.totals.cashOut.amount").isEqualTo("50.00");
		assertThat(events.stream(ShiftClosed.class)).singleElement().satisfies(closed -> {
			assertThat(closed.shiftId()).isEqualTo(UUID.fromString(id));
			assertThat(closed.forced()).isFalse();
			assertThat(closed.expectedCash().amount()).isEqualByComparingTo("80.00");
			assertThat(closed.difference().amount()).isEqualByComparingTo("5.00");
		});
	}

	@Test
	void aClosedShiftKeepsShowingItsFiguresAndIsImmutable() {
		var id = idOf(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID()));
		close(id, cashier, "{\"countedCash\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}");

		var read = get(SHIFTS + "/" + id, cashier, Role.CASHIER);

		assertThat(read).bodyJson().extractingPath("$.expectedCash.amount").isEqualTo("100.00");
		assertProblem(movement(id, cashier, "{\"type\":\"IN\",\"amount\":{\"amount\":\"1.00\",\"currency\":\"PEN\"},\"note\":\"x\"}"),
				HttpStatus.CONFLICT, "instore.shift-closed");
		assertThat(open(cashier, Role.CASHIER, "10.00", UUID.randomUUID())).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void aDifferenceWithinTheThresholdClosesWithoutANote() {
		var id = idOf(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID()));

		var result = close(id, cashier, "{\"countedCash\":{\"amount\":\"80.00\",\"currency\":\"PEN\"}}");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.difference.amount").isEqualTo("-20.00");
	}

	@Test
	void aDifferenceOverTheThresholdNeedsANoteAndTheCountCannotChangeOnceRevealed() {
		var id = idOf(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID()));

		var rejected = close(id, cashier, "{\"countedCash\":{\"amount\":\"60.00\",\"currency\":\"PEN\"}}");

		assertProblem(rejected, HttpStatus.CONFLICT, "instore.difference-note-required");
		assertThat(rejected).bodyJson().extractingPath("$.difference.amount").isEqualTo("-40.00");
		assertThat(status(id)).isEqualTo("OPEN");
		assertProblem(close(id, cashier, "{\"countedCash\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}"),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.counted-cash-locked");
		assertProblem(close(id, cashier, "{\"countedCash\":{\"amount\":\"60.00\",\"currency\":\"PEN\"},\"note\":\"  \"}"),
				HttpStatus.CONFLICT, "instore.difference-note-required");
		assertThat(events.stream(ShiftClosed.class)).isEmpty();

		var closed = close(id, cashier,
				"{\"countedCash\":{\"amount\":\"60.00\",\"currency\":\"PEN\"},\"note\":\"Paid the delivery boy\"}");

		assertThat(closed).hasStatusOk();
		assertThat(closed).bodyJson().extractingPath("$.differenceNote").isEqualTo("Paid the delivery boy");
		assertThat(closed).bodyJson().extractingPath("$.difference.amount").isEqualTo("-40.00");
	}

	@Test
	void onceTheDifferenceIsRevealedNothingElseMayChangeTheDrawer() {
		var id = idOf(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID()));
		close(id, cashier, "{\"countedCash\":{\"amount\":\"60.00\",\"currency\":\"PEN\"}}");

		var result = movement(id, cashier,
				"{\"type\":\"IN\",\"amount\":{\"amount\":\"40.00\",\"currency\":\"PEN\"},\"note\":\"Found it\"}");

		assertProblem(result, HttpStatus.CONFLICT, "instore.shift-closing");
		assertThat(jdbc.sql("select count(*) from instore.cash_movement where shift_id = ?").param(UUID.fromString(id)).query(Long.class).single()).isZero();
	}

	@Test
	void closingTwiceWithTheSameCountReplaysTheResultAndADifferentCountIsRejected() {
		var id = idOf(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID()));
		var body = "{\"countedCash\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}";
		close(id, cashier, body);

		var again = close(id, cashier, body);

		assertThat(again).hasStatusOk();
		assertThat(again).bodyJson().extractingPath("$.expectedCash.amount").isEqualTo("100.00");
		assertThat(events.stream(ShiftClosed.class)).hasSize(1);
		assertProblem(close(id, cashier, "{\"countedCash\":{\"amount\":\"90.00\",\"currency\":\"PEN\"}}"),
				HttpStatus.CONFLICT, "instore.shift-closed");
	}

	@Test
	void onlyTheShiftsCashierMayCloseItAndTheCountMustBeValid() {
		var id = idOf(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID()));

		assertProblem(close(id, UUID.randomUUID(), "{\"countedCash\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}"),
				HttpStatus.NOT_FOUND, "instore.shift-not-found");
		assertProblem(close(id, cashier, "{\"countedCash\":{\"amount\":\"-1.00\",\"currency\":\"PEN\"}}"),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-amount");
		assertThat(close(id, cashier, "{}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(status(id)).isEqualTo("OPEN");
	}

	@Test
	void anAdminForceClosesAForgottenShiftWithAReason() {
		var id = idOf(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID()));
		var admin = UUID.randomUUID();

		var result = forceClose(id, admin, Role.ADMIN, "{\"reason\":\"Forgotten overnight\"}");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
		assertThat(result).bodyJson().extractingPath("$.closeKind").isEqualTo("FORCED");
		assertThat(result).bodyJson().extractingPath("$.forceReason").isEqualTo("Forgotten overnight");
		assertThat(result).bodyJson().extractingPath("$.closedBy").isEqualTo(admin.toString());
		assertThat(result).bodyJson().extractingPath("$.expectedCash.amount").isEqualTo("100.00");
		assertThat(result).bodyJson().extractingPath("$.countedCash").isNull();
		assertThat(result).bodyJson().extractingPath("$.difference").isNull();
		assertThat(events.stream(ShiftClosed.class)).singleElement().satisfies(closed -> assertThat(closed.forced()).isTrue());
	}

	@Test
	void forceClosingNeedsAnAdminAReasonAndAnOpenShift() {
		var id = idOf(open(cashier, Role.CASHIER, "100.00", UUID.randomUUID()));

		assertThat(forceClose(id, cashier, Role.CASHIER, "{\"reason\":\"x\"}")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(forceClose(id, UUID.randomUUID(), Role.ADMIN, "{\"reason\":\" \"}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertProblem(forceClose(UUID.randomUUID(), UUID.randomUUID(), Role.ADMIN, "{\"reason\":\"x\"}"),
				HttpStatus.NOT_FOUND, "instore.shift-not-found");
		assertThat(forceClose(id, UUID.randomUUID(), Role.ADMIN, "{\"reason\":\"x\"}")).hasStatusOk();
		assertProblem(forceClose(id, UUID.randomUUID(), Role.ADMIN, "{\"reason\":\"x\"}"), HttpStatus.CONFLICT,
				"instore.shift-closed");
	}

	@Test
	void anAdminListsShiftsNewestFirstAndFiltersByStatus() {
		var first = idOf(open(cashier, Role.CASHIER, "1.00", UUID.randomUUID()));
		var second = idOf(open(UUID.randomUUID(), Role.CASHIER, "2.00", UUID.randomUUID()));
		close(first, cashier, "{\"countedCash\":{\"amount\":\"1.00\",\"currency\":\"PEN\"}}");

		var all = get(SHIFTS, UUID.randomUUID(), Role.ADMIN);
		var openOnes = get(SHIFTS + "?status=OPEN", UUID.randomUUID(), Role.ADMIN);

		assertThat(all).bodyJson().extractingPath("$.content[*].id").asList().containsExactly(second, first);
		assertThat(openOnes).bodyJson().extractingPath("$.content[*].id").asList().containsExactly(second);
		assertThat(openOnes).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
		assertThat(get(SHIFTS, cashier, Role.CASHIER)).hasStatus(HttpStatus.FORBIDDEN);
	}

	private static String openBody(String amount) {
		return "{\"openingFloat\":{\"amount\":\"%s\",\"currency\":\"PEN\"}}".formatted(amount);
	}

	private MvcTestResult open(UUID actor, Role role, String amount, UUID key) {
		return mvc.post()
			.uri(SHIFTS)
			.with(AuthenticatedAs.user(actor, role))
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(openBody(amount))
			.exchange();
	}

	private MvcTestResult get(String uri, UUID actor, Role role) {
		return mvc.get().uri(uri).with(AuthenticatedAs.user(actor, role)).exchange();
	}

	private MvcTestResult close(String id, UUID actor, String body) {
		return mvc.post()
			.uri(SHIFTS + "/" + id + "/close")
			.with(AuthenticatedAs.user(actor, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private MvcTestResult forceClose(String id, UUID actor, Role role, String body) {
		return mvc.post()
			.uri(SHIFTS + "/" + id + "/force-close")
			.with(AuthenticatedAs.user(actor, role))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private MvcTestResult forceClose(UUID id, UUID actor, Role role, String body) {
		return forceClose(id.toString(), actor, role, body);
	}

	private MvcTestResult movement(String shift, UUID actor, String body) {
		InstoreTables.aCashOutReason(jdbc, "Supplier");
		return mvc.post()
			.uri(SHIFTS + "/" + shift + "/cash-movements")
			.with(AuthenticatedAs.user(actor, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private String status(String shift) {
		return jdbc.sql("select status from instore.register_shift where id = ?")
			.param(UUID.fromString(shift))
			.query(String.class)
			.single();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

}
