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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.instore.TicketLinesSent;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class TicketSendIT {

	static final String TICKETS = "/api/v1/tickets";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	UUID table;

	UUID ticket;

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void anOpenTicketAtATable() {
		juice = InstoreTables.aSizedProduct(jdbc);
		table = InstoreTables.aTable(jdbc, "Table 4");
		ticket = UUID.fromString(idOf(open("{\"tableId\":\"%s\"}".formatted(table))));
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void sendingPutsTheUnsentLinesOnTheBoardAndMarksThemSent() {
		var line = addLine(juice.large(), 2, "No ice", true);

		var result = send(Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.lines[0].state").isEqualTo("SENT");
		assertThat(result).bodyJson().extractingPath("$.lines[0].sentAt").isNotNull();
		var card = cardOf(ticket);
		assertThat(PreparationTables.orderStatus(jdbc, card)).isEqualTo("WAITING");
		var board = jdbc.sql("""
				select short_number, channel, label, source, source_id from preparation.board_order where id = ?
				""").param(card).query().singleRow();
		assertThat(board).containsEntry("short_number", "T" + numberOf(ticket))
			.containsEntry("channel", "TABLE")
			.containsEntry("label", "Table 4")
			.containsEntry("source", "TICKET")
			.containsEntry("source_id", ticket);
		var boardLine = jdbc.sql("""
				select display_name, quantity, modifiers_text, note, allergy, station_id, state, product_id
				from preparation.board_line where board_order_id = ? and source_line_id = ?
				""").param(card).param(line).query().singleRow();
		assertThat(boardLine).containsEntry("quantity", 2)
			.containsEntry("modifiers_text", "Large")
			.containsEntry("note", "No ice")
			.containsEntry("allergy", true)
			.containsEntry("state", "PENDING")
			.containsEntry("product_id", juice.productId());
		assertThat(boardLine.get("display_name")).isEqualTo(productName(juice.productId()));
		assertThat(boardLine.get("station_id")).isEqualTo(defaultStation());
		assertThat(PreparationTables.markers(jdbc, card)).containsExactly("NEW*");
		assertThat(events.stream(TicketLinesSent.class)).singleElement().satisfies(sent -> {
			assertThat(sent.ticketId()).isEqualTo(ticket);
			assertThat(sent.boardSourceId()).isEqualTo(ticket);
			assertThat(sent.lineIds()).containsExactly(line);
			assertThat(sent.actorRole()).isEqualTo(Role.SERVER);
		});
	}

	@Test
	void aCounterTicketAppearsOnTheBoardAsACounterOrderLabelledWithTheCustomer() {
		var counter = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		addLineTo(counter, juice.small(), 1, null, false);

		assertThat(sendTo(counter, Role.CASHIER)).hasStatusOk();

		var board = jdbc.sql("select channel, label from preparation.board_order where id = ?")
			.param(cardOf(counter))
			.query()
			.singleRow();
		assertThat(board).containsEntry("channel", "COUNTER").containsEntry("label", "Ana");
	}

	@Test
	void sendingAgainSendsOnlyTheLinesAddedSinceAndLabelsThemModified() {
		var first = addLine(juice.small(), 1, null, false);
		send(Role.SERVER);
		var second = addLine(juice.large(), 1, null, false);

		var result = send(Role.SERVER);

		assertThat(result).hasStatusOk();
		var card = cardOf(ticket);
		assertThat(PreparationTables.count(jdbc, "board_order")).isEqualTo(1);
		assertThat(jdbc.sql("select source_line_id from preparation.board_line where board_order_id = ? order by position")
			.param(card)
			.query(UUID.class)
			.list()).containsExactly(first, second);
		assertThat(PreparationTables.markers(jdbc, card)).containsExactly("NEW*", "MODIFIED*");
		assertThat(events.stream(TicketLinesSent.class).map(TicketLinesSent::lineIds).toList())
			.containsExactly(List.of(first), List.of(second));
	}

	@Test
	void resendingWithNothingNewChangesNothing() {
		addLine(juice.small(), 1, null, false);
		send(Role.SERVER);
		var before = PreparationTables.markers(jdbc, cardOf(ticket));

		var again = send(Role.SERVER);

		assertThat(again).hasStatusOk();
		assertThat(again).bodyJson().extractingPath("$.lines[0].state").isEqualTo("SENT");
		assertThat(PreparationTables.markers(jdbc, cardOf(ticket))).isEqualTo(before);
		assertThat(PreparationTables.count(jdbc, "board_line")).isEqualTo(1);
		assertThat(events.stream(TicketLinesSent.class)).hasSize(1);
	}

	@Test
	void sendingATicketWithNoLinesPutsNothingOnTheBoard() {
		var result = send(Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(PreparationTables.count(jdbc, "board_order")).isZero();
		assertThat(events.stream(TicketLinesSent.class)).isEmpty();
	}

	@Test
	void sendingToACardThatWasReadySendsItBackToPreparing() {
		addLine(juice.small(), 1, null, false);
		send(Role.SERVER);
		PreparationTables.forceStatus(jdbc, cardOf(ticket), "READY");
		addLine(juice.large(), 1, null, false);

		send(Role.SERVER);

		assertThat(PreparationTables.orderStatus(jdbc, cardOf(ticket))).isEqualTo("PREPARING");
	}

	@Test
	void afterTheCardWasHandedOverTheNextSendOpensANewRoundOnTheBoard() {
		var first = addLine(juice.small(), 1, null, false);
		send(Role.SERVER);
		var firstCard = cardOf(ticket);
		PreparationTables.forceStatus(jdbc, firstCard, "HANDED_OVER");
		var second = addLine(juice.large(), 1, null, false);

		var result = send(Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(PreparationTables.count(jdbc, "board_order")).isEqualTo(2);
		assertThat(PreparationTables.orderStatus(jdbc, firstCard)).isEqualTo("HANDED_OVER");
		var secondSource = jdbc.sql("select board_source_id from instore.ticket where id = ?")
			.param(ticket)
			.query(UUID.class)
			.single();
		assertThat(secondSource).isNotEqualTo(ticket);
		var secondCard = jdbc.sql("select id from preparation.board_order where source = 'TICKET' and source_id = ?")
			.param(secondSource)
			.query(UUID.class)
			.single();
		assertThat(PreparationTables.orderStatus(jdbc, secondCard)).isEqualTo("WAITING");
		var secondBoard = jdbc.sql("select short_number, label, channel from preparation.board_order where id = ?")
			.param(secondCard)
			.query()
			.singleRow();
		assertThat(secondBoard).containsEntry("short_number", "T" + numberOf(ticket) + "-2")
			.containsEntry("label", "Table 4")
			.containsEntry("channel", "TABLE");
		assertThat(jdbc.sql("select source_line_id from preparation.board_line where board_order_id = ?")
			.param(secondCard)
			.query(UUID.class)
			.list()).containsExactly(second);
		assertThat(jdbc.sql("select board_source_id from instore.ticket_line where id = ?")
			.param(first)
			.query(UUID.class)
			.single()).isEqualTo(ticket);
		assertThat(jdbc.sql("select board_source_id from instore.ticket_line where id = ?")
			.param(second)
			.query(UUID.class)
			.single()).isEqualTo(secondSource);
		assertThat(events.stream(TicketLinesSent.class).map(TicketLinesSent::boardSourceId).toList())
			.containsExactly(ticket, secondSource);
	}

	@Test
	void afterTheCardWasCancelledTheNextSendOpensANewRound() {
		addLine(juice.small(), 1, null, false);
		send(Role.SERVER);
		PreparationTables.forceStatus(jdbc, cardOf(ticket), "CANCELLED");
		addLine(juice.large(), 1, null, false);

		assertThat(send(Role.SERVER)).hasStatusOk();

		assertThat(PreparationTables.count(jdbc, "board_order")).isEqualTo(2);
	}

	@Test
	void afterTheCardIsGoneFromTheBoardTheNextSendOpensANewRound() {
		addLine(juice.small(), 1, null, false);
		send(Role.SERVER);
		PreparationTables.clean(jdbc);
		var second = addLine(juice.large(), 1, null, false);

		var result = send(Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(PreparationTables.count(jdbc, "board_order")).isEqualTo(1);
		assertThat(jdbc.sql("select source_line_id from preparation.board_line").query(UUID.class).single())
			.isEqualTo(second);
		assertThat(jdbc.sql("select short_number from preparation.board_order").query(String.class).single())
			.isEqualTo("T" + numberOf(ticket) + "-2");
	}

	@Test
	void roundsKeepCountingAcrossHandovers() {
		for (var round = 1; round <= 3; round++) {
			addLine(juice.small(), 1, null, false);
			assertThat(send(Role.SERVER)).hasStatusOk();
			jdbc.sql("update preparation.board_order set status = 'HANDED_OVER'").update();
		}

		assertThat(jdbc.sql("select short_number from preparation.board_order order by sent_at, short_number")
			.query(String.class)
			.list()).containsExactlyInAnyOrder("T" + numberOf(ticket), "T" + numberOf(ticket) + "-2",
					"T" + numberOf(ticket) + "-3");
		assertThat(jdbc.sql("select board_rounds from instore.ticket where id = ?")
			.param(ticket)
			.query(Integer.class)
			.single()).isEqualTo(3);
	}

	@Test
	void anUnknownTicketIsNotFoundAndAClosedOneIsRejected() {
		assertProblem(sendTo(UUID.randomUUID(), Role.SERVER), HttpStatus.NOT_FOUND, "instore.ticket-not-found");
		addLine(juice.small(), 1, null, false);
		jdbc.sql("update instore.ticket set status = 'CLOSED', closed_at = now() where id = ?").param(ticket).update();

		assertProblem(send(Role.SERVER), HttpStatus.CONFLICT, "instore.ticket-closed");
		assertThat(PreparationTables.count(jdbc, "board_order")).isZero();
	}

	@Test
	void everyFloorRoleMaySend() {
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.ADMIN }) {
			addLine(juice.small(), 1, null, false);
			assertThat(send(role)).hasStatusOk();
		}
	}

	@Test
	void aCustomerMayNotSend() {
		addLine(juice.small(), 1, null, false);

		assertThat(send(Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(PreparationTables.count(jdbc, "board_order")).isZero();
	}

	@Test
	void anAnonymousCallerMayNotSend() {
		assertThat(mvc.post().uri(TICKETS + "/" + ticket + "/send").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// helpers

	private MvcTestResult open(String body) {
		return mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private UUID addLine(UUID option, int quantity, String note, boolean allergy) {
		return addLineTo(ticket, option, quantity, note, allergy);
	}

	private UUID addLineTo(UUID target, UUID option, int quantity, String note, boolean allergy) {
		var noteJson = note == null ? "null" : "\"" + note + "\"";
		var result = mvc.post()
			.uri(TICKETS + "/" + target + "/lines")
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":[\"%s\"],\"quantity\":%d,\"note\":%s,\"allergy\":%s}"
				.formatted(juice.productId(), option, quantity, noteJson, allergy))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(assertThat(result).bodyJson().extractingPath("$.lines[-1:].id").asList().actual().get(0).toString());
	}

	private MvcTestResult send(Role role) {
		return sendTo(ticket, role);
	}

	private MvcTestResult sendTo(UUID target, Role role) {
		return mvc.post().uri(TICKETS + "/" + target + "/send").with(as(role)).exchange();
	}

	private UUID cardOf(UUID source) {
		return jdbc.sql("select id from preparation.board_order where source = 'TICKET' and source_id = ?")
			.param(source)
			.query(UUID.class)
			.single();
	}

	private long numberOf(UUID target) {
		return jdbc.sql("select number from instore.ticket where id = ?").param(target).query(Long.class).single();
	}

	private String productName(UUID product) {
		return jdbc.sql("select name from catalog.product where id = ?").param(product).query(String.class).single();
	}

	private UUID defaultStation() {
		return jdbc.sql("select id from catalog.station where default_station").query(UUID.class).single();
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
