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
import com.jhanantezana.jugueria.instore.TicketCancelled;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class TicketCancelIT {

	static final String TICKETS = "/api/v1/tickets";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	UUID table;

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void anEmptyTable() {
		juice = InstoreTables.aSizedProduct(jdbc);
		InstoreTables.aVoidReason(jdbc, "Changed mind");
		table = InstoreTables.aTable(jdbc, "Table 4");
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void cancelsAnEmptyTicketAndFreesItsTable() {
		var ticket = openAt(table);

		var result = cancel(ticket, Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
		assertThat(InstoreTables.ticketStatus(jdbc, ticket)).isEqualTo("CANCELLED");
		var grid = mvc.get().uri("/api/v1/tables").with(as(Role.SERVER)).exchange();
		assertThat(grid).bodyJson().extractingPath("$[?(@.id=='" + table + "')].status").asList().containsExactly("FREE");
		assertThat(events.stream(TicketCancelled.class)).singleElement().satisfies(cancelled -> {
			assertThat(cancelled.ticketId()).isEqualTo(ticket);
			assertThat(cancelled.tableId()).isEqualTo(table);
		});
		assertThat(events.stream(TableOccupancyChanged.class).map(TableOccupancyChanged::tableId)).contains(table);
		assertThat(openAt(table)).isNotEqualTo(ticket);
	}

	@Test
	void aCancelledTicketNoLongerKeepsItsTableFromBeingDeactivated() {
		var ticket = openAt(table);
		assertThat(deactivate(table)).hasStatus(HttpStatus.CONFLICT);

		cancel(ticket, Role.CASHIER);

		assertThat(deactivate(table)).hasStatusOk();
	}

	@Test
	void dropsTheLinesThatNeverWentToTheKitchen() {
		var ticket = openAt(table);
		addLine(ticket);
		addLine(ticket);

		var result = cancel(ticket, Role.SERVER);

		assertThat(result).hasStatusOk();
		assertThat(jdbc.sql("select count(*) from instore.ticket_line where ticket_id = ?")
			.param(ticket)
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	void cancelsATicketWhoseEverySentLineWasVoided() {
		var ticket = openAt(table);
		var line = addLine(ticket);
		send(ticket);
		voidLine(ticket, line);

		var result = cancel(ticket, Role.CASHIER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
		assertThat(InstoreTables.lineState(jdbc, line)).isEqualTo("VOIDED");
	}

	@Test
	void cancelsACounterTicketToo() {
		var counter = UUID.fromString(idOf(mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange()));

		assertThat(cancel(counter, Role.SERVER)).hasStatusOk();
		assertThat(events.stream(TableOccupancyChanged.class)).isEmpty();
	}

	@Test
	void refusesATicketWithALiveLine() {
		var ticket = openAt(table);
		addLine(ticket);
		send(ticket);

		assertProblem(cancel(ticket, Role.SERVER), HttpStatus.CONFLICT, "instore.ticket-not-empty");
		assertThat(InstoreTables.ticketStatus(jdbc, ticket)).isEqualTo("OPEN");
		assertThat(events.stream(TicketCancelled.class)).isEmpty();
	}

	@Test
	void cancellingTwiceChangesNothingTheSecondTime() {
		var ticket = openAt(table);
		cancel(ticket, Role.SERVER);

		var again = cancel(ticket, Role.SERVER);

		assertThat(again).hasStatusOk();
		assertThat(again).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
		assertThat(events.stream(TicketCancelled.class)).hasSize(1);
	}

	@Test
	void aClosedTicketCannotBeCancelled() {
		var ticket = openAt(table);
		jdbc.sql("update instore.ticket set status = 'CLOSED', closed_at = now() where id = ?").param(ticket).update();

		assertProblem(cancel(ticket, Role.SERVER), HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void anUnknownTicketIsNotFound() {
		assertProblem(cancel(UUID.randomUUID(), Role.SERVER), HttpStatus.NOT_FOUND, "instore.ticket-not-found");
	}

	@Test
	void aCancelledTicketTakesNoMoreChanges() {
		var ticket = openAt(table);
		cancel(ticket, Role.SERVER);

		var added = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":[\"%s\"],\"quantity\":1,\"note\":null,\"allergy\":false}"
				.formatted(juice.productId(), juice.small()))
			.exchange();

		assertProblem(added, HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void everyFloorRoleMayCancelAndACustomerMayNot() {
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.ADMIN }) {
			var other = InstoreTables.aTable(jdbc);
			assertThat(cancel(openAt(other), role)).hasStatusOk();
		}
		assertThat(cancel(openAt(table), Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void anAnonymousCallerMayNotCancel() {
		var result = mvc.post().uri(TICKETS + "/" + openAt(table) + "/cancel").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// helpers

	private UUID openAt(UUID target) {
		var result = mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tableId\":\"%s\"}".formatted(target))
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

	private void voidLine(UUID target, UUID line) {
		var result = mvc.post()
			.uri(TICKETS + "/" + target + "/lines/" + line + "/void")
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Changed mind\"}")
			.exchange();
		assertThat(result).hasStatusOk();
	}

	private MvcTestResult cancel(UUID target, Role role) {
		return mvc.post().uri(TICKETS + "/" + target + "/cancel").with(as(role)).exchange();
	}

	private MvcTestResult deactivate(UUID target) {
		return mvc.post()
			.uri("/api/v1/admin/tables/" + target + "/deactivate")
			.with(as(Role.ADMIN))
			.header("If-Match", "\"0\"")
			.exchange();
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
