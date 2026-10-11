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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.instore.TicketLineVoided;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class TicketVoidIT {

	static final String TICKETS = "/api/v1/tickets";

	static final String CHANGED_MIND = "{\"reason\":\"Changed mind\"}";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	UUID ticket;

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void anOpenTicketAndReasons() {
		juice = InstoreTables.aSizedProduct(jdbc);
		InstoreTables.aVoidReason(jdbc, "Changed mind");
		InstoreTables.aVoidReason(jdbc, "Other");
		ticket = UUID.fromString(idOf(mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange()));
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void aServerVoidsASentLineBeforeItIsReadyAndItIsNotCharged() {
		var kept = sentLine();
		var line = sentLine();

		var result = voidLine(line, "{\"reason\":\"Changed mind\",\"note\":\"Wanted strawberry\"}", Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.total.amount").isEqualTo("5.00");
		assertThat(result).bodyJson()
			.extractingPath("$.lines[?(@.id=='" + line + "')].state")
			.asList()
			.containsExactly("VOIDED");
		assertThat(result).bodyJson()
			.extractingPath("$.lines[?(@.id=='" + line + "')].voidReasonCode")
			.asList()
			.containsExactly("Changed mind");
		assertThat(result).bodyJson()
			.extractingPath("$.lines[?(@.id=='" + line + "')].voidNote")
			.asList()
			.containsExactly("Wanted strawberry");
		assertThat(result).bodyJson()
			.extractingPath("$.lines[?(@.id=='" + line + "')].waste")
			.asList()
			.containsExactly(false);
		assertThat(PreparationTables.lineState(jdbc, boardLine(line))).isEqualTo("VOIDED");
		assertThat(PreparationTables.lineState(jdbc, boardLine(kept))).isEqualTo("PENDING");
		assertThat(PreparationTables.markers(jdbc, cardOf(ticket))).containsExactly("NEW*", "MODIFIED*", "VOID*");
		assertThat(events.stream(TicketLineVoided.class)).singleElement().satisfies(voided -> {
			assertThat(voided.ticketId()).isEqualTo(ticket);
			assertThat(voided.lineId()).isEqualTo(line);
			assertThat(voided.waste()).isFalse();
			assertThat(voided.reasonCode()).isEqualTo("Changed mind");
			assertThat(voided.note()).isEqualTo("Wanted strawberry");
			assertThat(voided.actorRole()).isEqualTo(Role.SERVER);
			assertThat(voided.quantity()).isEqualTo(1);
		});
	}

	@Test
	void aServerCannotVoidALineOnceItIsReady() {
		var line = sentLine();
		sentLine();
		markReady(line);

		var result = voidLine(line, CHANGED_MIND, Role.SERVER);

		assertProblem(result, HttpStatus.CONFLICT, "preparation.line-already-ready");
		assertThat(InstoreTables.lineState(jdbc, line)).isEqualTo("SENT");
		assertThat(PreparationTables.lineState(jdbc, boardLine(line))).isEqualTo("READY");
		assertThat(events.stream(TicketLineVoided.class)).isEmpty();
	}

	@Test
	void aCashierOrAdminVoidsAReadyLineAsWaste() {
		for (var role : new Role[] { Role.CASHIER, Role.ADMIN }) {
			var line = sentLine();
			markReady(line);

			var result = voidLine(line, CHANGED_MIND, role);

			assertThat(result).hasStatusOk();
			assertThat(result).bodyJson()
				.extractingPath("$.lines[?(@.id=='" + line + "')].waste")
				.asList()
				.containsExactly(true);
			assertThat(InstoreTables.lineState(jdbc, line)).isEqualTo("VOIDED");
		}
		assertThat(events.stream(TicketLineVoided.class)).allSatisfy(voided -> assertThat(voided.waste()).isTrue());
	}

	@Test
	void anUnsentLineIsDeletedNotVoided() {
		var line = addLine();

		assertProblem(voidLine(line, CHANGED_MIND, Role.SERVER), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.line-not-sent");
		assertThat(InstoreTables.lineState(jdbc, line)).isEqualTo("UNSENT");
	}

	@Test
	void rejectsAnUnknownLineOneOfAnotherTicketAndAClosedTicket() {
		var line = sentLine();

		assertProblem(voidLine(UUID.randomUUID(), CHANGED_MIND, Role.SERVER), HttpStatus.NOT_FOUND,
				"instore.line-not-found");
		var otherTicket = UUID.fromString(idOf(mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Luis\"}")
			.exchange()));
		assertProblem(voidLineOn(otherTicket, line, CHANGED_MIND, Role.SERVER, UUID.randomUUID(), UUID.randomUUID()),
				HttpStatus.NOT_FOUND, "instore.line-not-found");
		jdbc.sql("update instore.ticket set status = 'CLOSED', closed_at = now() where id = ?").param(ticket).update();
		assertProblem(voidLine(line, CHANGED_MIND, Role.SERVER), HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void theReasonMustBeOneOfTheActiveVoidReasons() {
		var line = sentLine();

		assertProblem(voidLine(line, "{\"reason\":\"Nonsense\"}", Role.SERVER), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-reason");
		jdbc.sql("update store.reason set active = false where type = 'VOID' and code = 'Changed mind'").update();
		assertProblem(voidLine(line, CHANGED_MIND, Role.SERVER), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-reason");
		assertThat(InstoreTables.lineState(jdbc, line)).isEqualTo("SENT");
	}

	@Test
	void theReasonOtherNeedsANote() {
		var line = sentLine();

		assertProblem(voidLine(line, "{\"reason\":\"Other\"}", Role.SERVER), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-reason");
		assertProblem(voidLine(line, "{\"reason\":\"other\",\"note\":\"  \"}", Role.SERVER),
				HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-reason");
		assertThat(voidLine(line, "{\"reason\":\"other\",\"note\":\"Spilled\"}", Role.SERVER)).hasStatusOk();
	}

	@Test
	void aMissingReasonOrAnOverlongNoteIsAValidationError() {
		var line = sentLine();

		assertThat(voidLine(line, "{}", Role.SERVER)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(voidLine(line, "{\"reason\":\"Changed mind\",\"note\":\"%s\"}".formatted("x".repeat(281)), Role.SERVER))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void voidingRequiresAnIdempotencyKey() {
		var line = sentLine();

		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines/" + line + "/void")
			.with(as(Role.SERVER))
			.contentType(MediaType.APPLICATION_JSON)
			.content(CHANGED_MIND)
			.exchange();

		assertProblem(result, HttpStatus.BAD_REQUEST, "common.idempotency-key-required");
	}

	@Test
	void retryingTheSameVoidReplaysTheFirstAnswerAndVoidsOnce() {
		var line = sentLine();
		sentLine();
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();

		var first = voidLineOn(ticket, line, CHANGED_MIND, Role.SERVER, actor, key);
		var retry = voidLineOn(ticket, line, CHANGED_MIND, Role.SERVER, actor, key);

		assertThat(first).hasStatusOk();
		assertThat(retry).hasStatusOk();
		assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(events.stream(TicketLineVoided.class)).hasSize(1);
		assertThat(PreparationTables.markers(jdbc, cardOf(ticket))).containsExactly("NEW*", "MODIFIED*", "VOID*");
	}

	@Test
	void reusingAKeyForAnotherVoidIsRejected() {
		var one = sentLine();
		var two = sentLine();
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		voidLineOn(ticket, one, CHANGED_MIND, Role.SERVER, actor, key);

		var result = voidLineOn(ticket, two, CHANGED_MIND, Role.SERVER, actor, key);

		assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "common.idempotency-key-reused");
		assertThat(InstoreTables.lineState(jdbc, two)).isEqualTo("SENT");
	}

	@Test
	void voidingAnAlreadyVoidedLineChangesNothingEvenAfterTheCardIsClosed() {
		var line = sentLine();
		assertThat(voidLine(line, CHANGED_MIND, Role.SERVER)).hasStatusOk();
		// Voiding the only line cancelled the card; a client retrying with a new key must still get a success.
		assertThat(PreparationTables.orderStatus(jdbc, cardOf(ticket))).isEqualTo("CANCELLED");

		var again = voidLine(line, "{\"reason\":\"Other\",\"note\":\"again\"}", Role.CASHIER);

		assertThat(again).hasStatusOk();
		assertThat(events.stream(TicketLineVoided.class)).hasSize(1);
		assertThat(jdbc.sql("select void_reason_code from instore.ticket_line where id = ?")
			.param(line)
			.query(String.class)
			.single()).isEqualTo("Changed mind");
	}

	@Test
	void voidingAnAlreadyVoidedLineAfterTheCardWasHandedOverChangesNothing() {
		var line = sentLine();
		sentLine();
		assertThat(voidLine(line, CHANGED_MIND, Role.SERVER)).hasStatusOk();
		PreparationTables.forceStatus(jdbc, cardOf(ticket), "HANDED_OVER");

		assertThat(voidLine(line, CHANGED_MIND, Role.SERVER)).hasStatusOk();
	}

	@Test
	void aServerCannotVoidALineOnAHandedOverCardButACashierVoidsItAsWaste() {
		var line = sentLine();
		handOver();

		assertProblem(voidLine(line, CHANGED_MIND, Role.SERVER), HttpStatus.CONFLICT, "preparation.line-already-ready");
		assertThat(InstoreTables.lineState(jdbc, line)).isEqualTo("SENT");

		var result = voidLine(line, CHANGED_MIND, Role.CASHIER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson()
			.extractingPath("$.lines[?(@.id=='" + line + "')].waste")
			.asList()
			.containsExactly(true);
		assertThat(InstoreTables.lineState(jdbc, line)).isEqualTo("VOIDED");
		assertThat(PreparationTables.orderStatus(jdbc, cardOf(ticket))).isEqualTo("HANDED_OVER");
	}

	@Test
	void retryingAWasteVoidOfAHandedOverLineIsASuccess() {
		var line = sentLine();
		handOver();
		assertThat(voidLine(line, CHANGED_MIND, Role.ADMIN)).hasStatusOk();

		assertThat(voidLine(line, CHANGED_MIND, Role.ADMIN)).hasStatusOk();
		assertThat(events.stream(TicketLineVoided.class)).hasSize(1);
	}

	private void handOver() {
		jdbc.sql("update preparation.board_line set state = 'READY', ready_at = now()").update();
		PreparationTables.forceStatus(jdbc, cardOf(ticket), "HANDED_OVER");
	}

	@Test
	void aLineOfALaterRoundIsVoidedOnItsOwnCard() {
		var first = sentLine();
		PreparationTables.forceStatus(jdbc, cardOf(ticket), "HANDED_OVER");
		var second = sentLine();
		var secondSource = jdbc.sql("select board_source_id from instore.ticket where id = ?")
			.param(ticket)
			.query(UUID.class)
			.single();

		var result = voidLine(second, CHANGED_MIND, Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(InstoreTables.lineState(jdbc, second)).isEqualTo("VOIDED");
		assertThat(InstoreTables.lineState(jdbc, first)).isEqualTo("SENT");
		assertThat(PreparationTables.orderStatus(jdbc, cardOf(secondSource))).isEqualTo("CANCELLED");
		assertThat(PreparationTables.orderStatus(jdbc, cardOf(ticket))).isEqualTo("HANDED_OVER");
	}

	@Test
	void afterEveryLineWasVoidedTheNextSendOpensANewRound() {
		var line = sentLine();
		voidLine(line, CHANGED_MIND, Role.SERVER);
		var replacement = addLine();

		var result = mvc.post().uri(TICKETS + "/" + ticket + "/send").with(as(Role.SERVER)).exchange();

		assertThat(result).hasStatusOk();
		assertThat(PreparationTables.count(jdbc, "board_order")).isEqualTo(2);
		assertThat(InstoreTables.lineState(jdbc, replacement)).isEqualTo("SENT");
	}

	@Test
	void everyFloorRoleMayVoidAndACustomerMayNot() {
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.ADMIN }) {
			assertThat(voidLine(sentLine(), CHANGED_MIND, role)).hasStatusOk();
		}
		assertThat(voidLine(sentLine(), CHANGED_MIND, Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void anAnonymousCallerMayNotVoid() {
		var line = sentLine();

		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines/" + line + "/void")
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(CHANGED_MIND)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// helpers

	private UUID addLine() {
		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":[\"%s\"],\"quantity\":1,\"note\":null,\"allergy\":false}"
				.formatted(juice.productId(), juice.small()))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(assertThat(result).bodyJson().extractingPath("$.lines[-1:].id").asList().actual().get(0).toString());
	}

	private UUID sentLine() {
		var line = addLine();
		assertThat(mvc.post().uri(TICKETS + "/" + ticket + "/send").with(as(Role.SERVER)).exchange()).hasStatusOk();
		return line;
	}

	private MvcTestResult voidLine(UUID line, String body, Role role) {
		return voidLineOn(ticket, line, body, role, UUID.randomUUID(), UUID.randomUUID());
	}

	private MvcTestResult voidLineOn(UUID target, UUID line, String body, Role role, UUID actor, UUID key) {
		return mvc.post()
			.uri(TICKETS + "/" + target + "/lines/" + line + "/void")
			.with(AuthenticatedAs.user(actor, role))
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private void markReady(UUID line) {
		jdbc.sql("update preparation.board_line set state = 'READY', ready_at = now() where source_line_id = ?")
			.param(line)
			.update();
		jdbc.sql("update preparation.board_order set status = 'PREPARING' where id = ?").param(cardOf(ticket)).update();
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

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

	private static RequestPostProcessor as(Role role) {
		return AuthenticatedAs.user(UUID.randomUUID(), role);
	}

}
