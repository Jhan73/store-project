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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.PreparationApi;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.PreparationTables;

/** The races of tech-spec §11 for tickets, on real threads and real PostgreSQL. */
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TicketRaceIT {

	static final String TICKETS = "/api/v1/tickets";

	static final int THREADS = 6;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PreparationApi preparation;

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void aProductAndAReason() {
		juice = InstoreTables.aSizedProduct(jdbc);
		InstoreTables.aVoidReason(jdbc, "Changed mind");
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void twoStaffOpeningTheSameTableAtOnceOpenOneTicket() throws Exception {
		var table = InstoreTables.aTable(jdbc);

		var results = race(THREADS, i -> () -> open("{\"tableId\":\"%s\"}".formatted(table)));

		assertThat(results.stream().filter(r -> r.getResponse().getStatus() == 201)).hasSize(1);
		assertThat(results.stream().filter(r -> r.getResponse().getStatus() == 409)).hasSize(THREADS - 1)
			.allSatisfy(r -> assertThat(r).bodyJson().extractingPath("$.code").isEqualTo("instore.table-occupied"));
		assertThat(jdbc.sql("select count(*) from instore.ticket where table_id = ? and status = 'OPEN'")
			.param(table)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void transferringTwoTicketsToTheSameFreeTableAtOnceMovesOnlyOne() throws Exception {
		var tableA = InstoreTables.aTable(jdbc);
		var tableB = InstoreTables.aTable(jdbc);
		var target = InstoreTables.aTable(jdbc);
		var first = UUID.fromString(idOf(open("{\"tableId\":\"%s\"}".formatted(tableA))));
		var second = UUID.fromString(idOf(open("{\"tableId\":\"%s\"}".formatted(tableB))));

		var results = race(2, i -> () -> transfer(i == 0 ? first : second, target));

		assertThat(results.stream().map(r -> r.getResponse().getStatus())).containsExactlyInAnyOrder(200, 409);
		assertThat(results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow())
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("instore.table-occupied");
		assertThat(jdbc.sql("select count(*) from instore.ticket where table_id = ? and status = 'OPEN'")
			.param(target)
			.query(Long.class)
			.single()).isEqualTo(1);
		assertThat(jdbc.sql("select count(distinct table_id) from instore.ticket").query(Long.class).single())
			.isEqualTo(2);
	}

	@Test
	void sendingTheSameTicketManyTimesAtOnceSendsItsLinesOnce() throws Exception {
		var ticket = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		addLine(ticket);
		addLine(ticket);

		var results = race(THREADS, i -> () -> send(ticket));

		assertThat(results).allSatisfy(r -> assertThat(r).hasStatusOk());
		assertThat(PreparationTables.count(jdbc, "board_order")).isEqualTo(1);
		assertThat(PreparationTables.count(jdbc, "board_line")).isEqualTo(2);
		assertThat(PreparationTables.count(jdbc, "board_marker")).isEqualTo(1);
		assertThat(jdbc.sql("select count(*) from instore.ticket_line where ticket_id = ? and state = 'SENT'")
			.param(ticket)
			.query(Long.class)
			.single()).isEqualTo(2);
		assertThat(jdbc.sql("select count(*) from audit.audit_log where action = 'TICKET_LINES_SENT' and entity_id = ?")
			.param(ticket)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void sendingWhileTheOnlySentLineIsVoidedNeverLosesTheNewLine() throws Exception {
		var ticket = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		var first = addLine(ticket);
		assertThat(send(ticket)).hasStatusOk();
		var second = addLine(ticket);

		var results = race(2, i -> i == 0 ? () -> voidLine(ticket, first) : () -> send(ticket));

		assertThat(results).allSatisfy(r -> assertThat(r).hasStatusOk());
		assertThat(InstoreTables.lineState(jdbc, first)).isEqualTo("VOIDED");
		assertThat(InstoreTables.lineState(jdbc, second)).isEqualTo("SENT");
		var card = jdbc.sql("select b.status from preparation.board_line l join preparation.board_order b "
				+ "on b.id = l.board_order_id where l.source_line_id = ?")
			.param(second)
			.query(String.class)
			.single();
		assertThat(card).isIn("WAITING", "PREPARING");
	}

	@Test
	void voidingTheSameLineTwiceAtOnceVoidsItOnce() throws Exception {
		var ticket = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		var first = addLine(ticket);
		addLine(ticket);
		assertThat(send(ticket)).hasStatusOk();

		var results = race(THREADS, i -> () -> voidLine(ticket, first));

		assertThat(results).allSatisfy(r -> assertThat(r).hasStatusOk());
		assertThat(jdbc.sql("select count(*) from audit.audit_log where action = 'TICKET_LINE_VOIDED' and entity_id = ?")
			.param(first)
			.query(Long.class)
			.single()).isEqualTo(1);
		assertThat(jdbc.sql("select count(*) from preparation.board_marker where kind = 'VOID'")
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void sendingAndTransferringAtOnceLeavesTheCardOnTheNewTable() throws Exception {
		var from = InstoreTables.aTable(jdbc, "Old table");
		var to = InstoreTables.aTable(jdbc, "New table");
		var ticket = UUID.fromString(idOf(open("{\"tableId\":\"%s\"}".formatted(from))));
		addLine(ticket);

		var results = race(2, i -> i == 0 ? () -> send(ticket) : () -> transfer(ticket, to));

		assertThat(results).allSatisfy(r -> assertThat(r).hasStatusOk());
		assertThat(jdbc.sql("select label from preparation.board_order").query(String.class).single())
			.isEqualTo("New table");
	}

	@Test
	void addingTheSameLineWithOneKeyManyTimesAtOnceAddsOneLine() throws Exception {
		var ticket = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		var body = lineBody();

		var results = race(THREADS, i -> () -> mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(AuthenticatedAs.user(actor, Role.SERVER))
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange());

		assertThat(results).allSatisfy(r -> assertThat(r).hasStatus(HttpStatus.CREATED));
		assertThat(results.stream().filter(r -> "true".equals(r.getResponse().getHeader("Idempotent-Replayed"))))
			.hasSize(THREADS - 1);
		assertThat(InstoreTables.count(jdbc, "ticket_line")).isEqualTo(1);
	}

	@Test
	void deactivatingATableWhileATicketOpensOnItNeverLeavesAnOpenTicketOnAnInactiveTable() throws Exception {
		for (var round = 0; round < 15; round++) {
			var table = InstoreTables.aTable(jdbc);

			var results = race(2, i -> i == 0 ? () -> open("{\"tableId\":\"%s\"}".formatted(table)) : () -> deactivate(table));

			var openTickets = jdbc.sql("select count(*) from instore.ticket where table_id = ? and status = 'OPEN'")
				.param(table)
				.query(Long.class)
				.single();
			var active = jdbc.sql("select active from instore.dining_table where id = ?")
				.param(table)
				.query(Boolean.class)
				.single();
			assertThat(openTickets > 0 && !active).as("round %d: open ticket on an inactive table", round).isFalse();
			assertThat(results.stream().map(r -> r.getResponse().getStatus())).as("round %d", round)
				.containsAnyOf(201, 200);
		}
	}

	@Test
	void swappingTwoTicketsBetweenTheirTablesAtOnceNeverFailsWithAServerError() throws Exception {
		for (var round = 0; round < 10; round++) {
			var tableA = InstoreTables.aTable(jdbc);
			var tableB = InstoreTables.aTable(jdbc);
			var first = UUID.fromString(idOf(open("{\"tableId\":\"%s\"}".formatted(tableA))));
			var second = UUID.fromString(idOf(open("{\"tableId\":\"%s\"}".formatted(tableB))));

			var results = race(2, i -> () -> i == 0 ? transfer(first, tableB) : transfer(second, tableA));

			assertThat(results.stream().map(r -> r.getResponse().getStatus())).as("round %d", round)
				.allMatch(status -> status == 200 || status == 409);
			assertThat(jdbc.sql("select max(c) from (select count(*) c from instore.ticket where status = 'OPEN' "
					+ "group by table_id) t").query(Long.class).single()).isEqualTo(1);
		}
	}

	@Test
	void sendingWhileTheCardIsHandedOverAlwaysLeavesTheNewLineOnALiveCard() throws Exception {
		for (var round = 0; round < 5; round++) {
			var ticket = UUID.fromString(idOf(open("{\"counterName\":\"Ana%d\"}".formatted(round))));
			addLine(ticket);
			assertThat(send(ticket)).hasStatusOk();
			var card = jdbc.sql("select id from preparation.board_order where source_id = ?")
				.param(ticket)
				.query(UUID.class)
				.single();
			jdbc.sql("update preparation.board_line set state = 'READY' where board_order_id = ?").param(card).update();
			PreparationTables.forceStatus(jdbc, card, "READY");
			var second = addLine(ticket);

			var results = race(2, i -> i == 0 ? () -> send(ticket) : () -> handOver(card));

			assertThat(results.get(0)).as("round %d send", round).hasStatusOk();
			assertThat(results.get(1).getResponse().getStatus()).as("round %d handover", round).isIn(200, 409);
			var holder = jdbc.sql("select b.status from preparation.board_line l join preparation.board_order b "
					+ "on b.id = l.board_order_id where l.source_line_id = ?").param(second).query(String.class).single();
			assertThat(holder).as("round %d", round).isIn("WAITING", "PREPARING");
		}
	}

	@Test
	void sendingWhileTheCardIsCancelledNeverErrorsAndEveryLineIsOnOneCard() throws Exception {
		var ticket = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		addLine(ticket);
		assertThat(send(ticket)).hasStatusOk();
		var second = addLine(ticket);

		var results = race(2, i -> i == 0 ? () -> send(ticket) : () -> cancelCard(ticket));

		assertThat(results.get(0)).hasStatusOk();
		assertThat(InstoreTables.lineState(jdbc, second)).isEqualTo("SENT");
		assertThat(jdbc.sql("select count(*) from preparation.board_line where source_line_id = ?")
			.param(second)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	@Test
	void sendingManyTimesAtOnceAfterAHandoverOpensExactlyOneNewRound() throws Exception {
		var ticket = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		addLine(ticket);
		assertThat(send(ticket)).hasStatusOk();
		PreparationTables.forceStatus(jdbc, jdbc.sql("select id from preparation.board_order")
			.query(UUID.class)
			.single(), "HANDED_OVER");
		addLine(ticket);
		addLine(ticket);

		var results = race(THREADS, i -> () -> send(ticket));

		assertThat(results).allSatisfy(r -> assertThat(r).hasStatusOk());
		assertThat(PreparationTables.count(jdbc, "board_order")).isEqualTo(2);
		assertThat(PreparationTables.count(jdbc, "board_line")).isEqualTo(3);
		assertThat(jdbc.sql("select board_rounds from instore.ticket where id = ?")
			.param(ticket)
			.query(Integer.class)
			.single()).isEqualTo(2);
	}

	@Test
	void voidingAReadyLineWhileTheCardIsHandedOverEndsVoidedWithoutErrors() throws Exception {
		var ticket = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		var line = addLine(ticket);
		addLine(ticket);
		assertThat(send(ticket)).hasStatusOk();
		var card = jdbc.sql("select id from preparation.board_order").query(UUID.class).single();
		jdbc.sql("update preparation.board_line set state = 'READY'").update();
		PreparationTables.forceStatus(jdbc, card, "READY");

		var results = race(2, i -> i == 0 ? () -> voidAsCashier(ticket, line) : () -> handOver(card));

		assertThat(results.get(0)).hasStatusOk();
		assertThat(InstoreTables.lineState(jdbc, line)).isEqualTo("VOIDED");
	}

	// helpers

	private MvcTestResult handOver(UUID card) {
		return mvc.post()
			.uri("/api/v1/board/orders/" + card + "/handover")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.exchange();
	}

	private MvcTestResult voidAsCashier(UUID ticket, UUID line) {
		return mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines/" + line + "/void")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();
	}

	private MvcTestResult cancelCard(UUID ticket) {
		try {
			preparation.cancel(BoardSource.TICKET, ticket);
		}
		catch (BusinessException e) {
			// Losing to staff starting the card is a legal outcome of this race.
		}
		return null;
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

	private MvcTestResult open(String body) {
		return mvc.post()
			.uri(TICKETS)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private String lineBody() {
		return "{\"productId\":\"%s\",\"modifierOptionIds\":[\"%s\"],\"quantity\":1,\"note\":null,\"allergy\":false}"
			.formatted(juice.productId(), juice.small());
	}

	private UUID addLine(UUID ticket) {
		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(lineBody())
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(assertThat(result).bodyJson().extractingPath("$.lines[-1:].id").asList().actual().get(0).toString());
	}

	private MvcTestResult send(UUID ticket) {
		return mvc.post()
			.uri(TICKETS + "/" + ticket + "/send")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.exchange();
	}

	private MvcTestResult transfer(UUID ticket, UUID table) {
		return mvc.post()
			.uri(TICKETS + "/" + ticket + "/transfer")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tableId\":\"%s\"}".formatted(table))
			.exchange();
	}

	private MvcTestResult voidLine(UUID ticket, UUID line) {
		return mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines/" + line + "/void")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();
	}

	private MvcTestResult deactivate(UUID table) {
		var etag = "\"" + jdbc.sql("select version from instore.dining_table where id = ?")
			.param(table)
			.query(Long.class)
			.single() + "\"";
		return mvc.post()
			.uri("/api/v1/admin/tables/" + table + "/deactivate")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.header("If-Match", etag)
			.exchange();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

}
