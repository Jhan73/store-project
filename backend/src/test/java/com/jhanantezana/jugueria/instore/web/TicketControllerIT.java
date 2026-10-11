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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TicketControllerIT {

	static final String TICKETS = "/api/v1/tickets";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void opensATicketForATable() {
		var table = InstoreTables.aTable(jdbc, "Table 4");

		var result = open("{\"tableId\":\"%s\",\"note\":\"Birthday\"}".formatted(table), Role.SERVER);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		var id = idOf(result);
		assertThat(result.getResponse().getHeader("Location")).isEqualTo(TICKETS + "/" + id);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("TABLE");
		assertThat(result).bodyJson().extractingPath("$.tableId").isEqualTo(table.toString());
		assertThat(result).bodyJson().extractingPath("$.tableName").isEqualTo("Table 4");
		assertThat(result).bodyJson().extractingPath("$.counterName").isNull();
		assertThat(result).bodyJson().extractingPath("$.note").isEqualTo("Birthday");
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("OPEN");
		assertThat(result).bodyJson().extractingPath("$.billRequested").isEqualTo(false);
		assertThat(result).bodyJson().extractingPath("$.total.amount").isEqualTo("0.00");
		assertThat(result).bodyJson().extractingPath("$.total.currency").isEqualTo("PEN");
		assertThat(result).bodyJson().extractingPath("$.lines").asList().isEmpty();
		assertThat(result).bodyJson().extractingPath("$.number").isNotNull();
		assertThat(result).bodyJson().extractingPath("$.openedAt").isNotNull();
	}

	@Test
	void opensATicketForTheCounterWithTheCustomersName() {
		var result = open("{\"counterName\":\"  Ana  \"}", Role.CASHIER);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("COUNTER");
		assertThat(result).bodyJson().extractingPath("$.counterName").isEqualTo("Ana");
		assertThat(result).bodyJson().extractingPath("$.tableId").isNull();
		assertThat(result).bodyJson().extractingPath("$.tableName").isNull();
	}

	@Test
	void numbersTicketsInTheOrderTheyAreOpened() {
		var first = number(open("{\"counterName\":\"Ana\"}", Role.SERVER));
		var second = number(open("{\"counterName\":\"Luis\"}", Role.SERVER));

		assertThat(second).isGreaterThan(first);
	}

	@Test
	void rejectsASecondOpenTicketOnTheSameTable() {
		var table = InstoreTables.aTable(jdbc);
		open("{\"tableId\":\"%s\"}".formatted(table), Role.SERVER);

		var result = open("{\"tableId\":\"%s\"}".formatted(table), Role.CASHIER);

		assertProblem(result, HttpStatus.CONFLICT, "instore.table-occupied");
		assertThat(InstoreTables.count(jdbc, "ticket")).isEqualTo(1);
	}

	@Test
	void rejectsAnUnknownTable() {
		var result = open("{\"tableId\":\"%s\"}".formatted(UUID.randomUUID()), Role.SERVER);

		assertProblem(result, HttpStatus.NOT_FOUND, "instore.table-not-found");
	}

	@Test
	void rejectsADeactivatedTable() {
		var table = InstoreTables.aTable(jdbc);
		InstoreTables.deactivate(jdbc, table);

		var result = open("{\"tableId\":\"%s\"}".formatted(table), Role.SERVER);

		assertProblem(result, HttpStatus.CONFLICT, "instore.table-inactive");
	}

	@Test
	void rejectsATicketThatNamesNeitherATableNorACounterCustomer() {
		assertProblem(open("{}", Role.SERVER), HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-ticket");
		assertProblem(open("{\"counterName\":\"   \"}", Role.SERVER), HttpStatus.UNPROCESSABLE_CONTENT,
				"instore.invalid-ticket");
	}

	@Test
	void rejectsATicketThatNamesBothATableAndACounterCustomer() {
		var table = InstoreTables.aTable(jdbc);

		var result = open("{\"tableId\":\"%s\",\"counterName\":\"Ana\"}".formatted(table), Role.SERVER);

		assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-ticket");
	}

	@Test
	void rejectsAnOverlongNoteWithAValidationError() {
		var result = open("{\"counterName\":\"Ana\",\"note\":\"%s\"}".formatted("x".repeat(141)), Role.SERVER);

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void openingRequiresAnIdempotencyKey() {
		var result = mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange();

		assertProblem(result, HttpStatus.BAD_REQUEST, "common.idempotency-key-required");
	}

	@Test
	void retryingTheSameOpenReturnsTheSameTicketAndOpensOnlyOne() {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		var body = "{\"counterName\":\"Ana\"}";

		var first = open(body, actor, Role.SERVER, key);
		var retry = open(body, actor, Role.SERVER, key);

		assertThat(first).hasStatus(HttpStatus.CREATED);
		assertThat(retry).hasStatus(HttpStatus.CREATED);
		assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(idOf(retry)).isEqualTo(idOf(first));
		assertThat(InstoreTables.count(jdbc, "ticket")).isEqualTo(1);
	}

	@Test
	void reusingAKeyForAnotherTicketIsRejected() {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		open("{\"counterName\":\"Ana\"}", actor, Role.SERVER, key);

		var result = open("{\"counterName\":\"Luis\"}", actor, Role.SERVER, key);

		assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "common.idempotency-key-reused");
		assertThat(InstoreTables.count(jdbc, "ticket")).isEqualTo(1);
	}

	@Test
	void readsATicketAndListsTheOpenOnesOldestFirst() {
		var first = idOf(open("{\"counterName\":\"Ana\"}", Role.SERVER));
		var second = idOf(open("{\"counterName\":\"Luis\"}", Role.SERVER));

		var one = mvc.get().uri(TICKETS + "/" + first).with(as(Role.SERVER)).exchange();
		var all = mvc.get().uri(TICKETS).with(as(Role.SERVER)).exchange();

		assertThat(one).hasStatusOk();
		assertThat(one).bodyJson().extractingPath("$.id").isEqualTo(first);
		assertThat(all).hasStatusOk();
		assertThat(all).bodyJson().extractingPath("$[*].id").asList().containsExactly(first, second);
		assertThat(all).bodyJson().extractingPath("$[0].total.amount").isEqualTo("0.00");
	}

	@Test
	void anUnknownTicketIsNotFound() {
		var result = mvc.get().uri(TICKETS + "/" + UUID.randomUUID()).with(as(Role.SERVER)).exchange();

		assertProblem(result, HttpStatus.NOT_FOUND, "instore.ticket-not-found");
	}

	@Test
	void everyFloorRoleMayOpenAndReadTickets() {
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.ADMIN }) {
			var created = open("{\"counterName\":\"Ana\"}", role);
			assertThat(created).hasStatus(HttpStatus.CREATED);
			assertThat(mvc.get().uri(TICKETS + "/" + idOf(created)).with(as(role)).exchange()).hasStatusOk();
			assertThat(mvc.get().uri(TICKETS).with(as(role)).exchange()).hasStatusOk();
		}
	}

	@Test
	void aCustomerMayNotOpenOrReadTickets() {
		assertThat(open("{\"counterName\":\"Ana\"}", Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri(TICKETS).with(as(Role.CUSTOMER)).exchange()).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri(TICKETS + "/" + UUID.randomUUID()).with(as(Role.CUSTOMER)).exchange())
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void anAnonymousCallerMayNotOpenOrReadTickets() {
		var open = mvc.post()
			.uri(TICKETS)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Ana\"}")
			.exchange();

		assertThat(open).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri(TICKETS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri(TICKETS + "/" + UUID.randomUUID()).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// helpers

	private MvcTestResult open(String body, Role role) {
		return open(body, UUID.randomUUID(), role, UUID.randomUUID());
	}

	private MvcTestResult open(String body, UUID actor, Role role, UUID key) {
		return mvc.post()
			.uri(TICKETS)
			.with(AuthenticatedAs.user(actor, role))
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static long number(MvcTestResult result) {
		return Long.parseLong(assertThat(result).bodyJson().extractingPath("$.number").actual().toString());
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

	private static RequestPostProcessor as(Role role) {
		return AuthenticatedAs.user(UUID.randomUUID(), role);
	}

}
