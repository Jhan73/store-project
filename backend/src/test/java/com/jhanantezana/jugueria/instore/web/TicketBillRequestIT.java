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
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class TicketBillRequestIT {

	static final String TICKETS = "/api/v1/tickets";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	UUID table;

	UUID ticket;

	@BeforeEach
	void aTicketAtATable() {
		table = InstoreTables.aTable(jdbc);
		var result = mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tableId\":\"%s\"}".formatted(table))
			.exchange();
		ticket = UUID.fromString(assertThat(result).bodyJson().extractingPath("$.id").actual().toString());
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void requestingTheBillMakesTheTableWaitForPaymentAndWithdrawingBringsItBack() {
		var requested = setBillRequested(ticket, true, Role.SERVER);

		assertThat(requested).hasStatusOk();
		assertThat(requested).bodyJson().extractingPath("$.billRequested").isEqualTo(true);
		assertThat(status()).isEqualTo("WAITING_FOR_PAYMENT");

		var withdrawn = setBillRequested(ticket, false, Role.CASHIER);

		assertThat(withdrawn).bodyJson().extractingPath("$.billRequested").isEqualTo(false);
		assertThat(status()).isEqualTo("OCCUPIED");
	}

	@Test
	void settingTheValueItAlreadyHasChangesNothing() {
		setBillRequested(ticket, true, Role.SERVER);
		var before = events.stream(TableOccupancyChanged.class).count();

		var again = setBillRequested(ticket, true, Role.SERVER);

		assertThat(again).hasStatusOk();
		assertThat(again).bodyJson().extractingPath("$.billRequested").isEqualTo(true);
		assertThat(events.stream(TableOccupancyChanged.class).count()).isEqualTo(before);
	}

	@Test
	void signalsTheGridOnlyWhenTheFlagChanges() {
		var opened = events.stream(TableOccupancyChanged.class).count();

		setBillRequested(ticket, true, Role.SERVER);
		setBillRequested(ticket, false, Role.SERVER);

		assertThat(events.stream(TableOccupancyChanged.class).count()).isEqualTo(opened + 2);
	}

	@Test
	void rejectsAnUnknownOrClosedTicket() {
		assertProblem(setBillRequested(UUID.randomUUID(), true, Role.SERVER), HttpStatus.NOT_FOUND,
				"instore.ticket-not-found");
		jdbc.sql("update instore.ticket set status = 'CLOSED', closed_at = now() where id = ?").param(ticket).update();

		assertProblem(setBillRequested(ticket, true, Role.SERVER), HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void aMissingValueIsAValidationError() {
		var result = mvc.put()
			.uri(TICKETS + "/" + ticket + "/bill-requested")
			.with(as(Role.SERVER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void everyFloorRoleMayRequestTheBillAndACustomerMayNot() {
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.ADMIN }) {
			assertThat(setBillRequested(ticket, true, role)).hasStatusOk();
		}
		assertThat(setBillRequested(ticket, true, Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void anAnonymousCallerMayNotRequestTheBill() {
		var result = mvc.put()
			.uri(TICKETS + "/" + ticket + "/bill-requested")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"requested\":true}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private String status() {
		var grid = mvc.get().uri("/api/v1/tables").with(as(Role.SERVER)).exchange();
		return assertThat(grid).bodyJson()
			.extractingPath("$[?(@.id=='" + table + "')].status")
			.asList()
			.actual()
			.get(0)
			.toString();
	}

	private MvcTestResult setBillRequested(UUID target, boolean requested, Role role) {
		return mvc.put()
			.uri(TICKETS + "/" + target + "/bill-requested")
			.with(as(role))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"requested\":%s}".formatted(requested))
			.exchange();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

	private static RequestPostProcessor as(Role role) {
		return AuthenticatedAs.user(UUID.randomUUID(), role);
	}

}
