package com.jhanantezana.jugueria.instore.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
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
import com.jhanantezana.jugueria.instore.TableOccupancyChanged;
import com.jhanantezana.jugueria.instore.TicketsMerged;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class TicketMergeIT {

	static final String TICKETS = "/api/v1/tickets";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	UUID cashier = UUID.randomUUID();

	UUID tableA;

	UUID tableB;

	UUID target;

	UUID source;

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void twoOpenTickets() {
		juice = InstoreTables.aSizedProduct(jdbc);
		InstoreTables.aVoidReason(jdbc, "Changed mind");
		tableA = InstoreTables.aTable(jdbc, "Table 4");
		tableB = InstoreTables.aTable(jdbc, "Table 9");
		target = openAt(tableA);
		source = openAt(tableB);
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void movesEveryLineOfTheSourceToTheTargetAndFreesItsTable() {
		var own = sentLine(target, juice.small());
		var sent = sentLine(source, juice.large());
		var voided = sentLine(source, juice.small());
		assertThat(voidLine(source, voided)).hasStatusOk();
		var unsent = addLine(source, juice.small());

		var result = merge(target, source, Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(target.toString());
		assertThat(result).bodyJson().extractingPath("$.lines[*].id").asList().containsExactlyInAnyOrder(own.toString(),
				sent.toString(), voided.toString(), unsent.toString());
		assertThat(result).bodyJson().extractingPath("$.lines[?(@.id=='" + sent + "')].state").asList()
			.containsExactly("SENT");
		assertThat(result).bodyJson().extractingPath("$.lines[?(@.id=='" + voided + "')].state").asList()
			.containsExactly("VOIDED");
		assertThat(result).bodyJson().extractingPath("$.lines[?(@.id=='" + unsent + "')].state").asList()
			.containsExactly("UNSENT");
		assertThat(result).bodyJson().extractingPath("$.total.amount").isEqualTo("18.00");
		assertThat(InstoreTables.ticketStatus(jdbc, source)).isEqualTo("MERGED");
		assertThat(jdbc.sql("select count(*) from instore.ticket_line where ticket_id = ?").param(source).query(Long.class).single())
			.isZero();
		assertThat(jdbc.sql("select merged_into_id from instore.ticket where id = ?").param(source).query(UUID.class).single())
			.isEqualTo(target);
		var grid = mvc.get().uri("/api/v1/tables").with(as(Role.SERVER)).exchange();
		assertThat(grid).bodyJson().extractingPath("$[?(@.id=='" + tableB + "')].status").asList().containsExactly("FREE");
		assertThat(grid).bodyJson().extractingPath("$[?(@.id=='" + tableA + "')].status").asList()
			.containsExactly("OCCUPIED");
		assertThat(events.stream(TicketsMerged.class)).singleElement().satisfies(merged -> {
			assertThat(merged.targetTicketId()).isEqualTo(target);
			assertThat(merged.sourceTicketId()).isEqualTo(source);
			assertThat(merged.movedLineIds()).containsExactlyInAnyOrder(sent, voided, unsent);
			assertThat(merged.sourceTableId()).isEqualTo(tableB);
			assertThat(merged.actorRole()).isEqualTo(Role.SERVER);
		});
		assertThat(events.stream(TableOccupancyChanged.class).map(TableOccupancyChanged::tableId)).contains(tableB);
	}

	@Test
	void aMergedTicketPointsToTheOneItWasFoldedInto() {
		sentLine(target, juice.small());
		sentLine(source, juice.large());
		assertThat(merge(target, source, Role.SERVER)).hasStatusOk();

		var merged = mvc.get().uri("/api/v1/tickets/" + source).with(as(Role.SERVER)).exchange();
		var survivor = mvc.get().uri("/api/v1/tickets/" + target).with(as(Role.SERVER)).exchange();

		assertThat(merged).bodyJson().extractingPath("$.status").isEqualTo("MERGED");
		assertThat(merged).bodyJson().extractingPath("$.mergedIntoId").isEqualTo(target.toString());
		assertThat(survivor).bodyJson().extractingPath("$.mergedIntoId").isNull();
	}

	@Test
	void theBoardCardsOfTheSourceFollowTheTargetsLabelAndKeepTheirLines() {
		sentLine(target, juice.small());
		var sent = sentLine(source, juice.large());
		var card = cardOf(source);

		assertThat(merge(target, source, Role.CASHIER)).hasStatusOk();

		assertThat(jdbc.sql("select label from preparation.board_order where id = ?").param(card).query(String.class).single())
			.isEqualTo("Table 4");
		assertThat(PreparationTables.orderStatus(jdbc, card)).isEqualTo("WAITING");
		assertThat(jdbc.sql("select count(*) from preparation.board_line where board_order_id = ? and source_line_id = ?")
			.param(card)
			.param(sent)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void everyRoundOfTheSourceIsRelabelled() {
		sentLine(source, juice.small());
		PreparationTables.forceStatus(jdbc, cardOf(source), "HANDED_OVER");
		sentLine(source, juice.small());

		assertThat(merge(target, source, Role.SERVER)).hasStatusOk();

		assertThat(jdbc.sql("select label from preparation.board_order").query(String.class).list())
			.containsOnly("Table 4");
	}

	@Test
	void aMovedSentLineCanStillBeVoidedAndReachesItsOwnCard() {
		var line = sentLine(source, juice.small());
		sentLine(source, juice.large());
		var card = cardOf(source);
		assertThat(merge(target, source, Role.SERVER)).hasStatusOk();

		var result = voidLine(target, line);

		assertThat(result).hasStatusOk();
		assertThat(jdbc.sql("select state from preparation.board_line where board_order_id = ? and source_line_id = ?")
			.param(card)
			.param(line)
			.query(String.class)
			.single()).isEqualTo("VOIDED");
	}

	@Test
	void theTargetKeepsSendingToItsOwnCardAfterwards() {
		sentLine(target, juice.small());
		sentLine(source, juice.small());
		assertThat(merge(target, source, Role.SERVER)).hasStatusOk();

		var next = sentLine(target, juice.large());

		assertThat(jdbc.sql("select count(*) from preparation.board_line where board_order_id = ? and source_line_id = ?")
			.param(cardOf(target))
			.param(next)
			.query(Long.class)
			.single()).isEqualTo(1);
		assertThat(PreparationTables.count(jdbc, "board_order")).isEqualTo(2);
	}

	@Test
	void aCounterTicketCanBeMergedIntoATableTicket() {
		var counter = openCounter("Ana");
		var line = sentLine(counter, juice.small());

		var result = merge(target, counter, Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.lines[0].id").isEqualTo(line.toString());
		assertThat(InstoreTables.ticketStatus(jdbc, counter)).isEqualTo("MERGED");
	}

	@Test
	void aSourceWithALivePaymentCannotBeMerged() {
		openShift();
		sentLine(source, juice.small());
		sentLine(source, juice.large());
		pay(source, "5.00");

		var result = merge(target, source, Role.CASHIER);

		assertProblem(result, HttpStatus.CONFLICT, "instore.ticket-has-payments");
		assertThat(InstoreTables.ticketStatus(jdbc, source)).isNotEqualTo("MERGED");
		assertThat(events.stream(TicketsMerged.class)).isEmpty();
	}

	@Test
	void aSourceWhosePaymentWasVoidedCanBeMerged() {
		openShift();
		sentLine(source, juice.small());
		sentLine(source, juice.large());
		var payment = pay(source, "5.00");
		var voided = mvc.post()
			.uri(TICKETS + "/" + source + "/payments/" + payment + "/void")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();
		assertThat(voided).hasStatusOk();

		assertThat(merge(target, source, Role.CASHIER)).hasStatusOk();
	}

	@Test
	void aTargetWithAPartialPaymentKeepsItAndOwesTheMovedLines() {
		openShift();
		sentLine(target, juice.small());
		sentLine(target, juice.small());
		pay(target, "5.00");
		sentLine(source, juice.large());

		var result = merge(target, source, Role.CASHIER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("OPEN");
		assertThat(result).bodyJson().extractingPath("$.paid.amount").isEqualTo("5.00");
		assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("13.00");
	}

	@Test
	void aTicketCannotBeMergedIntoItself() {
		assertProblem(merge(target, target, Role.SERVER), HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-merge");
	}

	@Test
	void unknownAndClosedTicketsAreRejected() {
		assertProblem(merge(target, UUID.randomUUID(), Role.SERVER), HttpStatus.NOT_FOUND, "instore.ticket-not-found");
		assertProblem(merge(UUID.randomUUID(), source, Role.SERVER), HttpStatus.NOT_FOUND, "instore.ticket-not-found");
		jdbc.sql("update instore.ticket set status = 'CLOSED', closed_at = now() where id = ?").param(source).update();
		assertProblem(merge(target, source, Role.SERVER), HttpStatus.CONFLICT, "instore.ticket-closed");
		jdbc.sql("update instore.ticket set status = 'OPEN', closed_at = null where id = ?").param(source).update();
		jdbc.sql("update instore.ticket set status = 'CLOSED', closed_at = now() where id = ?").param(target).update();
		assertProblem(merge(target, source, Role.SERVER), HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void mergingAgainTheSameTicketChangesNothing() {
		sentLine(source, juice.small());
		assertThat(merge(target, source, Role.SERVER)).hasStatusOk();

		var again = merge(target, source, Role.CASHIER);

		assertThat(again).hasStatusOk();
		assertThat(again).bodyJson().extractingPath("$.lines.length()").isEqualTo(1);
		assertThat(events.stream(TicketsMerged.class)).hasSize(1);
	}

	@Test
	void aSourceMergedIntoAnotherTicketCannotBeMergedIntoThisOne() {
		var third = openAt(InstoreTables.aTable(jdbc, "Table 1"));
		assertThat(merge(third, source, Role.SERVER)).hasStatusOk();

		assertProblem(merge(target, source, Role.SERVER), HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void theMergedTicketsCannotExceedTheLineLimit() {
		insertLines(target, 60);
		insertLines(source, 60);

		var result = merge(target, source, Role.SERVER);

		assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "instore.too-many-lines");
		assertThat(InstoreTables.ticketStatus(jdbc, source)).isEqualTo("OPEN");
	}

	@Test
	void voidedLinesDoNotCountTowardsTheLimit() {
		insertLines(target, 60);
		insertLines(source, 60);
		jdbc.sql("update instore.ticket_line set state = 'VOIDED', board_source_id = gen_random_uuid() where ticket_id = ?")
			.param(source)
			.update();

		assertThat(merge(target, source, Role.SERVER)).hasStatusOk();
	}

	@Test
	void oppositeMergesAtTheSameTimeLeaveExactlyOneWinnerAndNoDeadlock() throws Exception {
		sentLine(target, juice.small());
		sentLine(source, juice.large());
		var pool = Executors.newFixedThreadPool(2);
		try {
			List<Callable<MvcTestResult>> calls = List.of(() -> merge(target, source, Role.SERVER),
					() -> merge(source, target, Role.SERVER));
			var results = pool.invokeAll(calls);

			var statuses = results.stream().map(future -> {
				try {
					return future.get().getResponse().getStatus();
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}).sorted().toList();
			assertThat(statuses).containsExactly(200, 409);
			assertThat(jdbc.sql("select count(*) from instore.ticket where status = 'MERGED'").query(Long.class).single())
				.isEqualTo(1);
			assertThat(jdbc.sql("select count(*) from instore.ticket_line").query(Long.class).single()).isEqualTo(2);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@RepeatedTest(5)
	void identicalMergesAtTheSameTimeBothSucceedAndMergeOnce() throws Exception {
		sentLine(source, juice.large());
		var pool = Executors.newFixedThreadPool(2);
		try {
			List<Callable<MvcTestResult>> calls = List.of(() -> merge(target, source, Role.SERVER),
					() -> merge(target, source, Role.SERVER));
			var statuses = pool.invokeAll(calls).stream().map(future -> {
				try {
					return future.get().getResponse().getStatus();
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}).toList();

			assertThat(statuses).containsExactly(200, 200);
			assertThat(events.stream(TicketsMerged.class)).hasSize(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void everyFloorRoleMayMergeAndACustomerMayNot() {
		assertThat(merge(target, source, Role.SERVER)).hasStatusOk();
		var again = openAt(tableB);
		assertThat(merge(target, again, Role.CASHIER)).hasStatusOk();
		var third = openAt(tableB);
		assertThat(merge(target, third, Role.ADMIN)).hasStatusOk();
		var fourth = openAt(tableB);
		assertThat(merge(target, fourth, Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void anAnonymousCallerMayNotMerge() {
		var result = mvc.post()
			.uri(TICKETS + "/" + target + "/merge")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"sourceTicketId\":\"%s\"}".formatted(source))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void aMissingSourceIsAValidationError() {
		var result = mvc.post()
			.uri(TICKETS + "/" + target + "/merge")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// helpers

	private void insertLines(UUID ticket, int count) {
		jdbc.sql("""
				insert into instore.ticket_line (id, ticket_id, position, product_id, product_name, station_id, quantity,
					unit_price_amount, unit_price_currency, state, created_at, updated_at)
				select gen_random_uuid(), ?, g, ?, 'Juice', gen_random_uuid(), 1, 1.00, 'PEN', 'UNSENT', now(), now()
				from generate_series(1, ?) g
				""").param(ticket).param(juice.productId()).param(count).update();
	}

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

	private String pay(UUID ticket, String amount) {
		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/payments")
			.with(AuthenticatedAs.user(cashier, Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"method\":\"CARD\",\"amount\":{\"amount\":\"%s\",\"currency\":\"PEN\"}}".formatted(amount))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return assertThat(result).bodyJson().extractingPath("$.payments[0].id").actual().toString();
	}

	private UUID openAt(UUID table) {
		var result = mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tableId\":\"%s\"}".formatted(table))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(assertThat(result).bodyJson().extractingPath("$.id").actual().toString());
	}

	private UUID openCounter(String name) {
		var result = mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"%s\"}".formatted(name))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(assertThat(result).bodyJson().extractingPath("$.id").actual().toString());
	}

	private UUID addLine(UUID ticket, UUID option) {
		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":[\"%s\"],\"quantity\":1,\"note\":null,\"allergy\":false}"
				.formatted(juice.productId(), option))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(assertThat(result).bodyJson().extractingPath("$.lines[-1:].id").asList().actual().get(0).toString());
	}

	private UUID sentLine(UUID ticket, UUID option) {
		var line = addLine(ticket, option);
		assertThat(mvc.post().uri(TICKETS + "/" + ticket + "/send").with(as(Role.SERVER)).exchange()).hasStatusOk();
		return line;
	}

	private MvcTestResult voidLine(UUID ticket, UUID line) {
		return mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines/" + line + "/void")
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();
	}

	private MvcTestResult merge(UUID into, UUID from, Role role) {
		return mvc.post()
			.uri(TICKETS + "/" + into + "/merge")
			.with(as(role))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"sourceTicketId\":\"%s\"}".formatted(from))
			.exchange();
	}

	private UUID cardOf(UUID ticket) {
		return jdbc.sql("select id from preparation.board_order where source = 'TICKET' and source_id = ?")
			.param(ticket)
			.query(UUID.class)
			.single();
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor as(Role role) {
		return AuthenticatedAs.user(role == Role.CASHIER ? cashier : UUID.randomUUID(), role);
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

}
