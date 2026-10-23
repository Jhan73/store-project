package com.jhanantezana.jugueria.instore.web;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.jhanantezana.jugueria.instore.CashMovementRecorded;
import com.jhanantezana.jugueria.instore.CashMovementType;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class CashMovementControllerIT {

	static final String SHIFTS = "/api/v1/shifts";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	UUID cashier = UUID.randomUUID();

	String shift;

	@BeforeEach
	void anOpenShiftAndCashOutReasons() {
		InstoreTables.aCashOutReason(jdbc, "Supplier");
		InstoreTables.aCashOutReason(jdbc, "Other");
		shift = idOf(mvc.post()
			.uri(SHIFTS)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"openingFloat\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}")
			.exchange());
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void aCashierPaysASupplierFromTheDrawer() {
		var result = record(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"type\":\"OUT\",\"amount\":{\"amount\":\"35.50\",\"currency\":\"PEN\"},\"reasonCode\":\"supplier\",\"reference\":\"INV-77\"}");

		assertThat(result).hasStatus(HttpStatus.CREATED);
		var id = idOf(result);
		assertThat(result.getResponse().getHeader("Location")).isEqualTo(SHIFTS + "/" + shift + "/cash-movements/" + id);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("OUT");
		assertThat(result).bodyJson().extractingPath("$.amount.amount").isEqualTo("35.50");
		assertThat(result).bodyJson().extractingPath("$.reasonCode").isEqualTo("Supplier");
		assertThat(result).bodyJson().extractingPath("$.reference").isEqualTo("INV-77");
		assertThat(result).bodyJson().extractingPath("$.recordedBy").isEqualTo(cashier.toString());
		assertThat(events.stream(CashMovementRecorded.class)).singleElement().satisfies(recorded -> {
			assertThat(recorded.shiftId()).isEqualTo(UUID.fromString(shift));
			assertThat(recorded.type()).isEqualTo(CashMovementType.OUT);
			assertThat(recorded.amount().amount()).isEqualByComparingTo("35.50");
		});
	}

	@Test
	void aCashOutAboveTheCashInTheDrawerNeedsANoteExplainingIt() {
		var tooMuch = "\"amount\":{\"amount\":\"100.01\",\"currency\":\"PEN\"},\"reasonCode\":\"Supplier\"";

		assertProblem(record(cashier, Role.CASHIER, UUID.randomUUID(), "{\"type\":\"OUT\"," + tooMuch + "}"),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.cash-out-exceeds-drawer");
		assertThat(movementCount()).isZero();
		assertThat(record(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"type\":\"OUT\"," + tooMuch + ",\"note\":\"Supplier waited, sales not rung up yet\"}"))
			.hasStatus(HttpStatus.CREATED);
		assertProblem(record(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"type\":\"OUT\",\"amount\":{\"amount\":\"1.00\",\"currency\":\"PEN\"},\"reasonCode\":\"Supplier\"}"),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.cash-out-exceeds-drawer");
	}

	@Test
	void aCashOutUpToTheCashInTheDrawerNeedsNoNote() {
		assertThat(record(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"type\":\"OUT\",\"amount\":{\"amount\":\"100.00\",\"currency\":\"PEN\"},\"reasonCode\":\"Supplier\"}"))
			.hasStatus(HttpStatus.CREATED);
	}

	@Test
	void cashInNeedsANoteAndHasNoReasonList() {
		var in = "{\"type\":\"IN\",\"amount\":{\"amount\":\"20.00\",\"currency\":\"PEN\"},\"note\":\"Change from the bank\"}";

		assertThat(record(cashier, Role.CASHIER, UUID.randomUUID(), in)).hasStatus(HttpStatus.CREATED);
		assertProblem(record(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"type\":\"IN\",\"amount\":{\"amount\":\"20.00\",\"currency\":\"PEN\"}}"), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-cash-movement");
		assertProblem(record(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"type\":\"IN\",\"amount\":{\"amount\":\"20.00\",\"currency\":\"PEN\"},\"reasonCode\":\"Supplier\",\"note\":\"x\"}"),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-cash-movement");
	}

	@Test
	void cashOutNeedsAnActiveReasonAndOtherNeedsANote() {
		var amount = "\"amount\":{\"amount\":\"5.00\",\"currency\":\"PEN\"}";

		assertProblem(record(cashier, Role.CASHIER, UUID.randomUUID(), "{\"type\":\"OUT\"," + amount + "}"),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-reason");
		assertProblem(record(cashier, Role.CASHIER, UUID.randomUUID(), "{\"type\":\"OUT\"," + amount + ",\"reasonCode\":\"Nope\"}"),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-reason");
		assertProblem(record(cashier, Role.CASHIER, UUID.randomUUID(), "{\"type\":\"OUT\"," + amount + ",\"reasonCode\":\"Other\"}"),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-reason");
		assertThat(record(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"type\":\"OUT\"," + amount + ",\"reasonCode\":\"Other\",\"note\":\"Taxi\"}")).hasStatus(HttpStatus.CREATED);
		jdbc.sql("update store.reason set active = false where type = 'CASH_OUT' and code = 'Supplier'").update();
		assertProblem(record(cashier, Role.CASHIER, UUID.randomUUID(), "{\"type\":\"OUT\"," + amount + ",\"reasonCode\":\"Supplier\"}"),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-reason");
	}

	@Test
	void theAmountMustBePositiveInTheStoreCurrencyAndWithinTheColumn() {
		for (var amount : new String[] { "0.00", "-5.00", "10000000000.00" }) {
			assertProblem(record(cashier, Role.CASHIER, UUID.randomUUID(),
					"{\"type\":\"IN\",\"amount\":{\"amount\":\"%s\",\"currency\":\"PEN\"},\"note\":\"x\"}".formatted(amount)),
					HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-amount");
		}
		assertProblem(record(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"type\":\"IN\",\"amount\":{\"amount\":\"5.00\",\"currency\":\"USD\"},\"note\":\"x\"}"),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-amount");
		assertThat(movementCount()).isZero();
	}

	@Test
	void anOverlongReferenceOrAMissingTypeIsRejected() {
		assertThat(record(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"type\":\"IN\",\"amount\":{\"amount\":\"5.00\",\"currency\":\"PEN\"},\"note\":\"x\",\"reference\":\"%s\"}"
					.formatted("r".repeat(101)))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(record(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"amount\":{\"amount\":\"5.00\",\"currency\":\"PEN\"},\"note\":\"x\"}")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void retryingTheSameMovementRecordsItOnce() {
		var key = UUID.randomUUID();
		var body = "{\"type\":\"IN\",\"amount\":{\"amount\":\"5.00\",\"currency\":\"PEN\"},\"note\":\"x\"}";
		var first = record(cashier, Role.CASHIER, key, body);

		var retry = record(cashier, Role.CASHIER, key, body);

		assertThat(retry).hasStatus(HttpStatus.CREATED);
		assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(idOf(retry)).isEqualTo(idOf(first));
		assertThat(movementCount()).isEqualTo(1);
		assertThat(events.stream(CashMovementRecorded.class)).hasSize(1);
	}

	@Test
	void recordingRequiresAnIdempotencyKey() {
		var result = mvc.post()
			.uri(SHIFTS + "/" + shift + "/cash-movements")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"type\":\"IN\",\"amount\":{\"amount\":\"5.00\",\"currency\":\"PEN\"},\"note\":\"x\"}")
			.exchange();

		assertProblem(result, HttpStatus.BAD_REQUEST, "common.idempotency-key-required");
	}

	@Test
	void onlyTheShiftsOwnerRecordsOnItAndOnlyCashiersAndAdmins() {
		var body = "{\"type\":\"IN\",\"amount\":{\"amount\":\"5.00\",\"currency\":\"PEN\"},\"note\":\"x\"}";

		assertProblem(record(UUID.randomUUID(), Role.CASHIER, UUID.randomUUID(), body), HttpStatus.NOT_FOUND,
				"instore.shift-not-found");
		assertProblem(record(UUID.randomUUID(), Role.ADMIN, UUID.randomUUID(), body), HttpStatus.NOT_FOUND,
				"instore.shift-not-found");
		assertThat(record(UUID.randomUUID(), Role.SERVER, UUID.randomUUID(), body)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(record(UUID.randomUUID(), Role.CUSTOMER, UUID.randomUUID(), body)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(movementCount()).isZero();
	}

	@Test
	void theMovementsOfAShiftAreListedOldestFirstAndReadableByIdForItsOwnerAndAnAdmin() {
		var one = idOf(record(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"type\":\"IN\",\"amount\":{\"amount\":\"5.00\",\"currency\":\"PEN\"},\"note\":\"first\"}"));
		var two = idOf(record(cashier, Role.CASHIER, UUID.randomUUID(),
				"{\"type\":\"OUT\",\"amount\":{\"amount\":\"2.00\",\"currency\":\"PEN\"},\"reasonCode\":\"Supplier\"}"));

		var list = mvc.get().uri(SHIFTS + "/" + shift + "/cash-movements").with(AuthenticatedAs.user(cashier, Role.CASHIER)).exchange();
		var single = mvc.get()
			.uri(SHIFTS + "/" + shift + "/cash-movements/" + two)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.exchange();

		assertThat(list).hasStatusOk();
		assertThat(list).bodyJson().extractingPath("$[*].id").asList().containsExactly(one, two);
		assertThat(single).hasStatusOk();
		assertThat(single).bodyJson().extractingPath("$.id").isEqualTo(two);
		assertProblem(mvc.get()
			.uri(SHIFTS + "/" + shift + "/cash-movements/" + UUID.randomUUID())
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.exchange(), HttpStatus.NOT_FOUND, "instore.cash-movement-not-found");
		assertProblem(mvc.get()
			.uri(SHIFTS + "/" + shift + "/cash-movements")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.exchange(), HttpStatus.NOT_FOUND, "instore.shift-not-found");
	}

	private long movementCount() {
		return jdbc.sql("select count(*) from instore.cash_movement where shift_id = ?")
			.param(UUID.fromString(shift))
			.query(Long.class)
			.single();
	}

	private MvcTestResult record(UUID actor, Role role, UUID key, String body) {
		return mvc.post()
			.uri(SHIFTS + "/" + shift + "/cash-movements")
			.with(AuthenticatedAs.user(actor, role))
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

}
