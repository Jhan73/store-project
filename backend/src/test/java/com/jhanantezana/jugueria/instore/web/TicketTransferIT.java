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
import com.jhanantezana.jugueria.instore.TableOccupancyChanged;
import com.jhanantezana.jugueria.instore.TicketTransferred;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.PreparationTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class TicketTransferIT {

	static final String TICKETS = "/api/v1/tickets";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	UUID from;

	UUID to;

	UUID ticket;

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void aTicketAtTableFour() {
		juice = InstoreTables.aSizedProduct(jdbc);
		from = InstoreTables.aTable(jdbc, "Table 4");
		to = InstoreTables.aTable(jdbc, "Table 9");
		ticket = openAt(from);
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void movesTheTicketToAFreeTableAndTheGridFollows() {
		var line = addLine(ticket);
		send(ticket);

		var result = transfer(ticket, to, Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.tableId").isEqualTo(to.toString());
		assertThat(result).bodyJson().extractingPath("$.tableName").isEqualTo("Table 9");
		assertThat(result).bodyJson().extractingPath("$.lines[0].id").isEqualTo(line.toString());
		assertThat(result).bodyJson().extractingPath("$.lines[0].state").isEqualTo("SENT");
		var grid = mvc.get().uri("/api/v1/tables").with(as(Role.SERVER)).exchange();
		assertThat(grid).bodyJson().extractingPath("$[?(@.id=='" + from + "')].status").asList().containsExactly("FREE");
		assertThat(grid).bodyJson()
			.extractingPath("$[?(@.id=='" + to + "')].status")
			.asList()
			.containsExactly("OCCUPIED");
		assertThat(events.stream(TicketTransferred.class)).singleElement().satisfies(transferred -> {
			assertThat(transferred.ticketId()).isEqualTo(ticket);
			assertThat(transferred.fromTableId()).isEqualTo(from);
			assertThat(transferred.toTableId()).isEqualTo(to);
		});
		assertThat(events.stream(TableOccupancyChanged.class).map(TableOccupancyChanged::tableId))
			.contains(from, to);
	}

	@Test
	void relabelsTheCardOnTheBoardInTheSameStep() {
		addLine(ticket);
		send(ticket);

		transfer(ticket, to, Role.CASHIER);

		assertThat(jdbc.sql("select label from preparation.board_order where source_id = ?")
			.param(ticket)
			.query(String.class)
			.single()).isEqualTo("Table 9");
	}

	@Test
	void relabelsEveryRoundOfTheTicket() {
		addLine(ticket);
		send(ticket);
		PreparationTables.forceStatus(jdbc, cardOf(ticket), "HANDED_OVER");
		addLine(ticket);
		send(ticket);

		transfer(ticket, to, Role.SERVER);

		assertThat(jdbc.sql("select distinct label from preparation.board_order").query(String.class).list())
			.containsExactly("Table 9");
		assertThat(PreparationTables.count(jdbc, "board_order")).isEqualTo(2);
	}

	@Test
	void aTicketThatNeverReachedTheBoardMovesToo() {
		addLine(ticket);

		assertThat(transfer(ticket, to, Role.SERVER)).hasStatusOk();
	}

	@Test
	void rejectsATransferToATableThatAlreadyHasAnOpenTicket() {
		addLine(ticket);
		openAt(to);

		var result = transfer(ticket, to, Role.SERVER);

		assertProblem(result, HttpStatus.CONFLICT, "instore.table-occupied");
		assertThat(jdbc.sql("select table_id from instore.ticket where id = ?")
			.param(ticket)
			.query(UUID.class)
			.single()).isEqualTo(from);
		assertThat(events.stream(TicketTransferred.class)).isEmpty();
	}

	@Test
	void aFailedTransferLeavesTheBoardLabelAlone() {
		addLine(ticket);
		send(ticket);
		openAt(to);

		transfer(ticket, to, Role.SERVER);

		assertThat(jdbc.sql("select label from preparation.board_order where source_id = ?")
			.param(ticket)
			.query(String.class)
			.single()).isEqualTo("Table 4");
	}

	@Test
	void transferringToTheTableItAlreadyHasChangesNothing() {
		var result = transfer(ticket, from, Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.tableId").isEqualTo(from.toString());
		assertThat(events.stream(TicketTransferred.class)).isEmpty();
	}

	@Test
	void rejectsAnUnknownOrDeactivatedTargetTable() {
		assertProblem(transfer(ticket, UUID.randomUUID(), Role.SERVER), HttpStatus.NOT_FOUND, "instore.table-not-found");
		InstoreTables.deactivate(jdbc, to);

		assertProblem(transfer(ticket, to, Role.SERVER), HttpStatus.CONFLICT, "instore.table-inactive");
	}

	@Test
	void aCounterTicketHasNoTableToLeave() {
		var counter = UUID.fromString(idOf(mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange()));

		assertProblem(transfer(counter, to, Role.SERVER), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.ticket-has-no-table");
	}

	@Test
	void rejectsAnUnknownOrClosedTicket() {
		assertProblem(transfer(UUID.randomUUID(), to, Role.SERVER), HttpStatus.NOT_FOUND, "instore.ticket-not-found");
		jdbc.sql("update instore.ticket set status = 'CLOSED', closed_at = now() where id = ?").param(ticket).update();

		assertProblem(transfer(ticket, to, Role.SERVER), HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void aMissingTargetIsAValidationError() {
		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/transfer")
			.with(as(Role.SERVER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void everyFloorRoleMayTransferAndACustomerMayNot() {
		var tables = new UUID[] { to, InstoreTables.aTable(jdbc), InstoreTables.aTable(jdbc) };
		var roles = new Role[] { Role.SERVER, Role.CASHIER, Role.ADMIN };
		for (var i = 0; i < roles.length; i++) {
			assertThat(transfer(ticket, tables[i], roles[i])).hasStatusOk();
		}
		assertThat(transfer(ticket, from, Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void anAnonymousCallerMayNotTransfer() {
		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/transfer")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tableId\":\"%s\"}".formatted(to))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// helpers

	private UUID openAt(UUID table) {
		var result = mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tableId\":\"%s\"}".formatted(table))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(idOf(result));
	}

	private UUID addLine(UUID target) {
		var result = mvc.post()
			.uri(TICKETS + "/" + target + "/lines")
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":[\"%s\"],\"quantity\":1,\"note\":null,\"allergy\":false}"
				.formatted(juice.productId(), juice.small()))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(assertThat(result).bodyJson().extractingPath("$.lines[-1:].id").asList().actual().get(0).toString());
	}

	private void send(UUID target) {
		assertThat(mvc.post().uri(TICKETS + "/" + target + "/send").with(as(Role.SERVER)).exchange()).hasStatusOk();
	}

	private MvcTestResult transfer(UUID target, UUID table, Role role) {
		return mvc.post()
			.uri(TICKETS + "/" + target + "/transfer")
			.with(as(role))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tableId\":\"%s\"}".formatted(table))
			.exchange();
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
