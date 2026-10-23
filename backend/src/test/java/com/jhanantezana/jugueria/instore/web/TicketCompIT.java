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
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.instore.SaleRecorded;
import com.jhanantezana.jugueria.instore.TicketLineComped;
import com.jhanantezana.jugueria.inventory.internal.StockService;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class TicketCompIT {

	static final String TICKETS = "/api/v1/tickets";

	static final String STAFF_MEAL = "{\"reason\":\"Staff meal\"}";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@Autowired
	StockService stock;

	UUID cashier = UUID.randomUUID();

	UUID ticket;

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void anOpenTicketAndReasons() {
		juice = InstoreTables.aSizedProduct(jdbc);
		InstoreTables.aCompReason(jdbc, "Staff meal");
		InstoreTables.aCompReason(jdbc, "Other");
		InstoreTables.aVoidReason(jdbc, "Changed mind");
		openShift();
		var opened = mvc.post()
			.uri(TICKETS)
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange();
		ticket = UUID.fromString(assertThat(opened).bodyJson().extractingPath("$.id").actual().toString());
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void aCashierCompsASentLineAndItIsNotChargedButCountsAsWaste() {
		var kept = sentLine(juice.small());
		var line = sentLine(juice.large());

		var result = comp(line, "{\"reason\":\"Staff meal\",\"note\":\"Cook's lunch\"}", Role.CASHIER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.total.amount").isEqualTo("5.00");
		assertThat(result).bodyJson().extractingPath("$.lines[?(@.id=='" + line + "')].state").asList()
			.containsExactly("COMPED");
		assertThat(result).bodyJson().extractingPath("$.lines[?(@.id=='" + line + "')].waste").asList()
			.containsExactly(true);
		assertThat(result).bodyJson().extractingPath("$.lines[?(@.id=='" + line + "')].compReasonCode").asList()
			.containsExactly("Staff meal");
		assertThat(result).bodyJson().extractingPath("$.lines[?(@.id=='" + line + "')].compNote").asList()
			.containsExactly("Cook's lunch");
		assertThat(PreparationTables.lineState(jdbc, boardLine(line))).isEqualTo("PENDING");
		assertThat(jdbc.sql("select comped from preparation.board_line where id = ?")
			.param(boardLine(line))
			.query(Boolean.class)
			.single()).isTrue();
		assertThat(PreparationTables.lineState(jdbc, boardLine(kept))).isEqualTo("PENDING");
		assertThat(events.stream(TicketLineComped.class)).singleElement().satisfies(comped -> {
			assertThat(comped.ticketId()).isEqualTo(ticket);
			assertThat(comped.lineId()).isEqualTo(line);
			assertThat(comped.quantity()).isEqualTo(1);
			assertThat(comped.reasonCode()).isEqualTo("Staff meal");
			assertThat(comped.note()).isEqualTo("Cook's lunch");
			assertThat(comped.actorId()).isEqualTo(cashier);
			assertThat(comped.actorRole()).isEqualTo(Role.CASHIER);
			assertThat(comped.amount().amount()).isEqualByComparingTo("8.00");
			assertThat(comped.ticketClosed()).isFalse();
		});
		assertThat(jdbc.sql("select comped_by from instore.ticket_line where id = ?").param(line).query(UUID.class).single())
			.isEqualTo(cashier);
	}

	@Test
	void anAdminAlsoComps() {
		sentLine(juice.small());
		var line = sentLine(juice.small());

		assertThat(comp(line, STAFF_MEAL, Role.ADMIN)).hasStatusOk();
		assertThat(InstoreTables.lineState(jdbc, line)).isEqualTo("COMPED");
	}

	@Test
	void aServerAndACustomerCannotComp() {
		var line = sentLine(juice.small());

		assertThat(comp(line, STAFF_MEAL, Role.SERVER)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(comp(line, STAFF_MEAL, Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(InstoreTables.lineState(jdbc, line)).isEqualTo("SENT");
		assertThat(events.stream(TicketLineComped.class)).isEmpty();
	}

	@Test
	void anAnonymousCallerMayNotComp() {
		var line = sentLine(juice.small());

		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines/" + line + "/comp")
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(STAFF_MEAL)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void aReadyLineIsCompedToo() {
		var line = sentLine(juice.small());
		sentLine(juice.small());
		jdbc.sql("update preparation.board_line set state = 'READY', ready_at = now() where source_line_id = ?")
			.param(line)
			.update();
		jdbc.sql("update preparation.board_order set status = 'PREPARING' where id = ?").param(cardOf(ticket)).update();

		assertThat(comp(line, STAFF_MEAL, Role.CASHIER)).hasStatusOk();

		assertThat(PreparationTables.lineState(jdbc, boardLine(line))).isEqualTo("READY");
	}

	@Test
	void aCompTakesTheUnitsOffTheShelfAgainstTheLine() {
		stock.track(juice.productId(), true, 0);
		stock.recordEntry(juice.productId(), 10, null);
		sentLine(juice.small());
		var line = sentLine(juice.small());

		assertThat(comp(line, STAFF_MEAL, Role.CASHIER)).hasStatusOk();

		var waste = jdbc.sql("select on_hand_delta, reference_id from inventory.stock_movement where type = 'WASTE' and product_id = ?")
			.param(juice.productId())
			.query()
			.listOfRows();
		assertThat(waste).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("on_hand_delta", -1).containsEntry("reference_id", line));
	}

	@Test
	void anUnsentLineVoidedLineAndUnknownLineCannotBeComped() {
		var voided = sentLine(juice.small());
		sentLine(juice.small());
		assertThat(voidLine(voided)).hasStatusOk();
		var unsent = addLine(juice.small());

		assertProblem(comp(unsent, STAFF_MEAL, Role.CASHIER), HttpStatus.UNPROCESSABLE_CONTENT, "instore.line-not-sent");
		assertProblem(comp(voided, STAFF_MEAL, Role.CASHIER), HttpStatus.UNPROCESSABLE_CONTENT, "instore.line-voided");
		assertProblem(comp(UUID.randomUUID(), STAFF_MEAL, Role.CASHIER), HttpStatus.NOT_FOUND, "instore.line-not-found");
		assertThat(InstoreTables.lineState(jdbc, unsent)).isEqualTo("UNSENT");
	}

	@Test
	void aCompedLineCannotBeVoidedAfterwards() {
		var line = sentLine(juice.small());
		sentLine(juice.small());
		assertThat(comp(line, STAFF_MEAL, Role.CASHIER)).hasStatusOk();

		assertProblem(voidLine(line), HttpStatus.UNPROCESSABLE_CONTENT, "instore.line-comped");
		assertThat(InstoreTables.lineState(jdbc, line)).isEqualTo("COMPED");
	}

	@Test
	void aClosedOrUnknownTicketRejectsAComp() {
		var line = sentLine(juice.small());

		assertProblem(compOn(UUID.randomUUID(), line, STAFF_MEAL, cashier, Role.CASHIER, UUID.randomUUID()),
				HttpStatus.NOT_FOUND, "instore.ticket-not-found");
		jdbc.sql("update instore.ticket set status = 'CLOSED', closed_at = now() where id = ?").param(ticket).update();
		assertProblem(comp(line, STAFF_MEAL, Role.CASHIER), HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void theReasonMustBeAnActiveCompReason() {
		var line = sentLine(juice.small());

		assertProblem(comp(line, "{\"reason\":\"Changed mind\"}", Role.CASHIER), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-reason");
		assertProblem(comp(line, "{\"reason\":\"Nonsense\"}", Role.CASHIER), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-reason");
		jdbc.sql("update store.reason set active = false where type = 'COMP' and code = 'Staff meal'").update();
		assertProblem(comp(line, STAFF_MEAL, Role.CASHIER), HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-reason");
		assertThat(InstoreTables.lineState(jdbc, line)).isEqualTo("SENT");
	}

	@Test
	void theReasonOtherNeedsANote() {
		var line = sentLine(juice.small());

		assertProblem(comp(line, "{\"reason\":\"Other\"}", Role.CASHIER), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-reason");
		assertThat(comp(line, "{\"reason\":\"other\",\"note\":\"Owner's friend\"}", Role.CASHIER)).hasStatusOk();
	}

	@Test
	void aMissingReasonOrAnOverlongNoteIsAValidationError() {
		var line = sentLine(juice.small());

		assertThat(comp(line, "{}", Role.CASHIER)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(comp(line, "{\"reason\":\"Staff meal\",\"note\":\"%s\"}".formatted("x".repeat(281)), Role.CASHIER))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void compingRequiresAnIdempotencyKey() {
		var line = sentLine(juice.small());

		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines/" + line + "/comp")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content(STAFF_MEAL)
			.exchange();

		assertProblem(result, HttpStatus.BAD_REQUEST, "common.idempotency-key-required");
	}

	@Test
	void retryingTheSameCompReplaysTheFirstAnswerAndCompsOnce() {
		var line = sentLine(juice.small());
		sentLine(juice.small());
		var key = UUID.randomUUID();

		var first = compOn(ticket, line, STAFF_MEAL, cashier, Role.CASHIER, key);
		var retry = compOn(ticket, line, STAFF_MEAL, cashier, Role.CASHIER, key);

		assertThat(first).hasStatusOk();
		assertThat(retry).hasStatusOk();
		assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(events.stream(TicketLineComped.class)).hasSize(1);
	}

	@Test
	void reusingAKeyForAnotherCompIsRejected() {
		var one = sentLine(juice.small());
		var two = sentLine(juice.small());
		var key = UUID.randomUUID();
		compOn(ticket, one, STAFF_MEAL, cashier, Role.CASHIER, key);

		var result = compOn(ticket, two, STAFF_MEAL, cashier, Role.CASHIER, key);

		assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "common.idempotency-key-reused");
		assertThat(InstoreTables.lineState(jdbc, two)).isEqualTo("SENT");
	}

	@Test
	void compingALineAlreadyCompedChangesNothing() {
		var line = sentLine(juice.small());
		sentLine(juice.small());
		assertThat(comp(line, STAFF_MEAL, Role.CASHIER)).hasStatusOk();

		var again = comp(line, "{\"reason\":\"Other\",\"note\":\"again\"}", Role.ADMIN);

		assertThat(again).hasStatusOk();
		assertThat(events.stream(TicketLineComped.class)).hasSize(1);
		assertThat(jdbc.sql("select comp_reason_code from instore.ticket_line where id = ?")
			.param(line)
			.query(String.class)
			.single()).isEqualTo("Staff meal");
	}

	@Test
	void retryingACompThatClosedTheTicketStillSucceeds() {
		var small = sentLine(juice.small());
		var large = sentLine(juice.large());
		pay(List.of(small), "5.00");
		assertThat(comp(large, STAFF_MEAL, Role.CASHIER)).hasStatusOk();
		assertThat(InstoreTables.ticketStatus(jdbc, ticket)).isEqualTo("CLOSED");

		var retry = comp(large, STAFF_MEAL, Role.CASHIER);

		assertThat(retry).hasStatusOk();
		assertThat(retry).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
		assertThat(events.stream(TicketLineComped.class)).hasSize(1);
	}

	@Test
	void aCompedLineOfAClosedTicketStillRejectsOtherLines() {
		var small = sentLine(juice.small());
		var large = sentLine(juice.large());
		pay(List.of(small), "5.00");
		assertThat(comp(large, STAFF_MEAL, Role.CASHIER)).hasStatusOk();

		assertProblem(comp(small, STAFF_MEAL, Role.CASHIER), HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void aLineCoveredByAPaymentCannotBeCompedUntilThePaymentIsVoided() {
		var small = sentLine(juice.small());
		sentLine(juice.large());
		var payment = pay(List.of(small), "5.00");

		assertProblem(comp(small, STAFF_MEAL, Role.CASHIER), HttpStatus.CONFLICT, "instore.line-covered-by-payment");
		assertThat(InstoreTables.lineState(jdbc, small)).isEqualTo("SENT");

		var voided = mvc.post()
			.uri(TICKETS + "/" + ticket + "/payments/" + payment + "/void")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();
		assertThat(voided).hasStatusOk();

		assertThat(comp(small, STAFF_MEAL, Role.CASHIER)).hasStatusOk();
	}

	@Test
	void aLineCannotBeCompedWhenThePaymentsWouldExceedWhatIsLeft() {
		sentLine(juice.small());
		var large = sentLine(juice.large());
		pay(null, "10.00");

		assertProblem(comp(large, STAFF_MEAL, Role.CASHIER), HttpStatus.CONFLICT, "instore.line-covered-by-payment");
	}

	@Test
	void compingTheLastUnpaidLineClosesATicketThatIsNowFullyPaid() {
		var small = sentLine(juice.small());
		var large = sentLine(juice.large());
		pay(List.of(small), "5.00");

		var result = comp(large, STAFF_MEAL, Role.CASHIER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
		assertThat(events.stream(SaleRecorded.class)).singleElement().satisfies(sale -> {
			assertThat(sale.total().amount()).isEqualByComparingTo("5.00");
			assertThat(sale.lines()).extracting(line -> line.lineId()).containsExactly(small);
		});
		var order = events.stream()
			.filter(PayloadApplicationEvent.class::isInstance)
			.map(event -> ((PayloadApplicationEvent<?>) event).getPayload())
			.filter(payload -> payload instanceof TicketLineComped || payload instanceof SaleRecorded)
			.map(payload -> payload.getClass().getSimpleName())
			.toList();
		assertThat(order).containsExactly("TicketLineComped", "SaleRecorded");
		assertThat(events.stream(TicketLineComped.class)).singleElement()
			.satisfies(comped -> assertThat(comped.ticketClosed()).isTrue());
	}

	@Test
	void aTicketWithOnlyCompedLinesHasNothingToPayAndCanBeCancelled() {
		var line = sentLine(juice.small());
		assertThat(comp(line, STAFF_MEAL, Role.CASHIER)).hasStatusOk();

		var cancel = mvc.post().uri(TICKETS + "/" + ticket + "/cancel").with(AuthenticatedAs.user(cashier, Role.CASHIER)).exchange();

		assertThat(cancel).hasStatusOk();
		assertThat(InstoreTables.ticketStatus(jdbc, ticket)).isEqualTo("CANCELLED");
	}

	// helpers

	private void openShift() {
		var result = mvc.post()
			.uri("/api/v1/shifts")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"openingFloat\":{\"amount\":\"100.00\",\"currency\":\"PEN\"}}")
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
	}

	private String pay(List<UUID> lineIds, String amount) {
		var body = "{\"method\":\"CARD\",\"amount\":{\"amount\":\"%s\",\"currency\":\"PEN\"}%s}".formatted(amount,
				lineIds == null ? "" : ",\"lineIds\":" + lineIds.stream().map(l -> "\"" + l + "\"").toList());
		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/payments")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return assertThat(result).bodyJson().extractingPath("$.payments[0].id").actual().toString();
	}

	private UUID addLine(UUID option) {
		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":[\"%s\"],\"quantity\":1,\"note\":null,\"allergy\":false}"
				.formatted(juice.productId(), option))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(assertThat(result).bodyJson().extractingPath("$.lines[-1:].id").asList().actual().get(0).toString());
	}

	private UUID sentLine(UUID option) {
		var line = addLine(option);
		assertThat(mvc.post().uri(TICKETS + "/" + ticket + "/send").with(AuthenticatedAs.user(cashier, Role.CASHIER)).exchange())
			.hasStatusOk();
		return line;
	}

	private MvcTestResult comp(UUID line, String body, Role role) {
		return compOn(ticket, line, body, role == Role.CASHIER ? cashier : UUID.randomUUID(), role, UUID.randomUUID());
	}

	private MvcTestResult compOn(UUID target, UUID line, String body, UUID actor, Role role, UUID key) {
		return mvc.post()
			.uri(TICKETS + "/" + target + "/lines/" + line + "/comp")
			.with(AuthenticatedAs.user(actor, role))
			.header("Idempotency-Key", key.toString())
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
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();
	}

	private UUID boardLine(UUID line) {
		return jdbc.sql("select id from preparation.board_line where source_line_id = ?")
			.param(line)
			.query(UUID.class)
			.single();
	}

	private UUID cardOf(UUID source) {
		return jdbc.sql("select id from preparation.board_order where source = 'TICKET' and source_id = ?")
			.param(source)
			.query(UUID.class)
			.single();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

}
