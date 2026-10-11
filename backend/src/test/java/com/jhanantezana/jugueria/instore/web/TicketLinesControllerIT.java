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
class TicketLinesControllerIT {

	static final String TICKETS = "/api/v1/tickets";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	UUID ticket;

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void aTicketAndAProduct() {
		juice = InstoreTables.aSizedProduct(jdbc);
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
	void addsALineThatSnapshotsTheNameTheOptionsAndThePrice() {
		var result = addLine(line(juice.productId(), 2, "No ice", true, juice.large()), Role.SERVER);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result.getResponse().getHeader("Location")).isEqualTo(TICKETS + "/" + ticket);
		assertThat(result).bodyJson().extractingPath("$.lines").asList().hasSize(1);
		assertThat(result).bodyJson().extractingPath("$.lines[0].productId").isEqualTo(juice.productId().toString());
		assertThat(result).bodyJson().extractingPath("$.lines[0].quantity").isEqualTo(2);
		assertThat(result).bodyJson().extractingPath("$.lines[0].unitPrice.amount").isEqualTo("8.00");
		assertThat(result).bodyJson().extractingPath("$.lines[0].lineTotal.amount").isEqualTo("16.00");
		assertThat(result).bodyJson().extractingPath("$.lines[0].modifiers[0].name").isEqualTo("Large");
		assertThat(result).bodyJson().extractingPath("$.lines[0].modifiers[0].priceDelta.amount").isEqualTo("3.00");
		assertThat(result).bodyJson().extractingPath("$.lines[0].note").isEqualTo("No ice");
		assertThat(result).bodyJson().extractingPath("$.lines[0].allergy").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.lines[0].state").isEqualTo("UNSENT");
		assertThat(result).bodyJson().extractingPath("$.lines[0].sentAt").isNull();
		assertThat(result).bodyJson().extractingPath("$.total.amount").isEqualTo("16.00");
	}

	@Test
	void aLineKeepsItsPriceWhenTheCatalogPriceChanges() {
		addLine(line(juice.productId(), 1, null, false, juice.small()), Role.SERVER);
		jdbc.sql("update catalog.product set price_amount = 9.00 where id = ?").param(juice.productId()).update();

		var result = mvc.get().uri(TICKETS + "/" + ticket).with(as(Role.SERVER)).exchange();

		assertThat(result).bodyJson().extractingPath("$.lines[0].unitPrice.amount").isEqualTo("5.00");
		assertThat(result).bodyJson().extractingPath("$.total.amount").isEqualTo("5.00");
	}

	@Test
	void keepsLinesInTheOrderTheyWereAdded() {
		addLine(line(juice.productId(), 1, "first", false, juice.small()), Role.SERVER);
		var result = addLine(line(juice.productId(), 1, "second", false, juice.large()), Role.CASHIER);

		assertThat(result).bodyJson().extractingPath("$.lines[*].note").asList().containsExactly("first", "second");
		assertThat(result).bodyJson().extractingPath("$.total.amount").isEqualTo("13.00");
	}

	@Test
	void rejectsALineForAProductThatDoesNotExistOrIsNotOnSale() {
		assertProblem(addLine(line(UUID.randomUUID(), 1, null, false), Role.SERVER), HttpStatus.NOT_FOUND,
				"catalog.product-not-found");
		var plain = InstoreTables.aProduct(jdbc);
		jdbc.sql("update catalog.product set available = false where id = ?").param(plain).update();

		assertProblem(addLine(line(plain, 1, null, false), Role.SERVER), HttpStatus.CONFLICT,
				"catalog.product-unavailable");
		assertThat(InstoreTables.count(jdbc, "ticket_line")).isZero();
	}

	@Test
	void rejectsASelectionThatBreaksTheProductsModifierRules() {
		var result = addLine(line(juice.productId(), 1, null, false), Role.SERVER);

		assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "catalog.invalid-modifier-selection");
	}

	@Test
	void rejectsAQuantityOutsideOneToNinetyNineAndAnOverlongNote() {
		assertThat(addLine(line(juice.productId(), 0, null, false, juice.small()), Role.SERVER))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(addLine(line(juice.productId(), 100, null, false, juice.small()), Role.SERVER))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(addLine(line(juice.productId(), 1, "x".repeat(141), false, juice.small()), Role.SERVER))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void rejectsALineOnAnUnknownOrClosedTicket() {
		var unknown = mvc.post()
			.uri(TICKETS + "/" + UUID.randomUUID() + "/lines")
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(line(juice.productId(), 1, null, false, juice.small()))
			.exchange();
		assertProblem(unknown, HttpStatus.NOT_FOUND, "instore.ticket-not-found");
		jdbc.sql("update instore.ticket set status = 'CLOSED', closed_at = now() where id = ?").param(ticket).update();

		var closed = addLine(line(juice.productId(), 1, null, false, juice.small()), Role.SERVER);

		assertProblem(closed, HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void addingRequiresAnIdempotencyKey() {
		var result = mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(as(Role.SERVER))
			.contentType(MediaType.APPLICATION_JSON)
			.content(line(juice.productId(), 1, null, false, juice.small()))
			.exchange();

		assertProblem(result, HttpStatus.BAD_REQUEST, "common.idempotency-key-required");
	}

	@Test
	void retryingTheSameAddAddsTheLineOnce() {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		var body = line(juice.productId(), 1, null, false, juice.small());

		var first = addLine(body, actor, Role.SERVER, key);
		var retry = addLine(body, actor, Role.SERVER, key);

		assertThat(first).hasStatus(HttpStatus.CREATED);
		assertThat(retry).hasStatus(HttpStatus.CREATED);
		assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(InstoreTables.count(jdbc, "ticket_line")).isEqualTo(1);
	}

	@Test
	void reusingAKeyForAnotherLineIsRejected() {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		addLine(line(juice.productId(), 1, null, false, juice.small()), actor, Role.SERVER, key);

		var result = addLine(line(juice.productId(), 2, null, false, juice.small()), actor, Role.SERVER, key);

		assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "common.idempotency-key-reused");
		assertThat(InstoreTables.count(jdbc, "ticket_line")).isEqualTo(1);
	}

	@Test
	void changesAnUnsentLineFreely() {
		var line = lineIdOf(addLine(line(juice.productId(), 1, "old", false, juice.small()), Role.SERVER));
		var other = InstoreTables.aProduct(jdbc);

		var result = changeLine(line, line(other, 3, "new", true), Role.CASHIER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.lines").asList().hasSize(1);
		assertThat(result).bodyJson().extractingPath("$.lines[0].id").isEqualTo(line);
		assertThat(result).bodyJson().extractingPath("$.lines[0].productId").isEqualTo(other.toString());
		assertThat(result).bodyJson().extractingPath("$.lines[0].quantity").isEqualTo(3);
		assertThat(result).bodyJson().extractingPath("$.lines[0].note").isEqualTo("new");
		assertThat(result).bodyJson().extractingPath("$.lines[0].modifiers").asList().isEmpty();
		assertThat(result).bodyJson().extractingPath("$.total.amount").isEqualTo("15.00");
	}

	@Test
	void aSentLineIsNeverEditedInPlace() {
		var line = lineIdOf(addLine(line(juice.productId(), 1, null, false, juice.small()), Role.SERVER));
		markSent(line);

		var result = changeLine(line, line(juice.productId(), 2, null, false, juice.small()), Role.ADMIN);

		assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "instore.line-already-sent");
		assertThat(jdbc.sql("select quantity from instore.ticket_line where id = ?")
			.param(UUID.fromString(line))
			.query(Integer.class)
			.single()).isEqualTo(1);
	}

	@Test
	void aSentLineIsReportedAsSentEvenWhenItsProductCannotBeSoldAnymore() {
		var plain = InstoreTables.aProduct(jdbc);
		var line = lineIdOf(addLine(line(plain, 1, null, false), Role.SERVER));
		markSent(line);
		jdbc.sql("update catalog.product set available = false where id = ?").param(plain).update();

		var result = changeLine(line, line(plain, 2, null, false), Role.SERVER);

		assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "instore.line-already-sent");
	}

	@Test
	void deletesAnUnsentLine() {
		var line = lineIdOf(addLine(line(juice.productId(), 1, null, false, juice.small()), Role.SERVER));

		var result = deleteLine(line, Role.SERVER);

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(InstoreTables.count(jdbc, "ticket_line")).isZero();
	}

	@Test
	void aSentLineCannotBeDeleted() {
		var line = lineIdOf(addLine(line(juice.productId(), 1, null, false, juice.small()), Role.SERVER));
		markSent(line);

		assertProblem(deleteLine(line, Role.CASHIER), HttpStatus.UNPROCESSABLE_CONTENT, "instore.line-already-sent");
		assertThat(InstoreTables.count(jdbc, "ticket_line")).isEqualTo(1);
	}

	@Test
	void anUnknownLineOrOneOfAnotherTicketIsNotFound() {
		assertProblem(deleteLine(UUID.randomUUID().toString(), Role.SERVER), HttpStatus.NOT_FOUND,
				"instore.line-not-found");
		var elsewhere = UUID.fromString(idOf(mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"counterName\":\"Luis\"}")
			.exchange()));
		var line = lineIdOf(addLine(line(juice.productId(), 1, null, false, juice.small()), Role.SERVER));

		var result = mvc.delete()
			.uri(TICKETS + "/" + elsewhere + "/lines/" + line)
			.with(as(Role.SERVER))
			.exchange();

		assertProblem(result, HttpStatus.NOT_FOUND, "instore.line-not-found");
		assertProblem(changeLine(UUID.randomUUID().toString(), line(juice.productId(), 1, null, false, juice.small()),
				Role.SERVER), HttpStatus.NOT_FOUND, "instore.line-not-found");
	}

	@Test
	void repeatsLinesAsNewUnsentLinesPricedAtTodaysPrice() {
		var first = lineIdOf(addLine(line(juice.productId(), 2, "No ice", true, juice.large()), Role.SERVER));
		markSent(first);
		jdbc.sql("update catalog.product set price_amount = 6.00 where id = ?").param(juice.productId()).update();

		var result = repeat("{\"lineIds\":[\"%s\"]}".formatted(first), Role.SERVER);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.lines").asList().hasSize(2);
		assertThat(result).bodyJson().extractingPath("$.lines[1].state").isEqualTo("UNSENT");
		assertThat(result).bodyJson().extractingPath("$.lines[1].quantity").isEqualTo(2);
		assertThat(result).bodyJson().extractingPath("$.lines[1].note").isEqualTo("No ice");
		assertThat(result).bodyJson().extractingPath("$.lines[1].allergy").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.lines[1].modifiers[0].name").isEqualTo("Large");
		assertThat(result).bodyJson().extractingPath("$.lines[1].unitPrice.amount").isEqualTo("9.00");
		assertThat(result).bodyJson().extractingPath("$.lines[0].unitPrice.amount").isEqualTo("8.00");
		assertThat(result).bodyJson().extractingPath("$.lines[1].id").isNotEqualTo(first);
	}

	@Test
	void repeatingALineThatCannotBeSoldAnymoreChangesNothing() {
		var one = lineIdOf(addLine(line(juice.productId(), 1, null, false, juice.small()), Role.SERVER));
		var plain = InstoreTables.aProduct(jdbc);
		var two = lineIdOf(addLine(line(plain, 1, null, false), Role.SERVER));
		jdbc.sql("update catalog.product set available = false where id = ?").param(plain).update();

		var result = repeat("{\"lineIds\":[\"%s\",\"%s\"]}".formatted(one, two), Role.SERVER);

		assertProblem(result, HttpStatus.CONFLICT, "catalog.product-unavailable");
		assertThat(InstoreTables.count(jdbc, "ticket_line")).isEqualTo(2);
	}

	@Test
	void repeatingAnUnknownLineIsNotFoundAndAnEmptyListIsInvalid() {
		assertProblem(repeat("{\"lineIds\":[\"%s\"]}".formatted(UUID.randomUUID()), Role.SERVER), HttpStatus.NOT_FOUND,
				"instore.line-not-found");
		assertThat(repeat("{\"lineIds\":[]}", Role.SERVER)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void retryingTheSameRepeatRepeatsOnce() {
		var first = lineIdOf(addLine(line(juice.productId(), 1, null, false, juice.small()), Role.SERVER));
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		var body = "{\"lineIds\":[\"%s\"]}".formatted(first);

		repeat(body, actor, Role.SERVER, key);
		var retry = repeat(body, actor, Role.SERVER, key);

		assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
		assertThat(InstoreTables.count(jdbc, "ticket_line")).isEqualTo(2);
	}

	@Test
	void everyFloorRoleMayEditLines() {
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.ADMIN }) {
			var added = addLine(line(juice.productId(), 1, null, false, juice.small()), role);
			assertThat(added).hasStatus(HttpStatus.CREATED);
			var line = lineIdOf(added);
			assertThat(changeLine(line, line(juice.productId(), 2, null, false, juice.small()), role)).hasStatusOk();
			assertThat(repeat("{\"lineIds\":[\"%s\"]}".formatted(line), role)).hasStatus(HttpStatus.CREATED);
			assertThat(deleteLine(line, role)).hasStatus(HttpStatus.NO_CONTENT);
		}
	}

	@Test
	void aCustomerMayNotEditLines() {
		var line = UUID.randomUUID().toString();

		assertThat(addLine(line(juice.productId(), 1, null, false, juice.small()), Role.CUSTOMER))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(changeLine(line, line(juice.productId(), 1, null, false, juice.small()), Role.CUSTOMER))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(deleteLine(line, Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(repeat("{\"lineIds\":[\"%s\"]}".formatted(line), Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void anAnonymousCallerMayNotEditLines() {
		var line = UUID.randomUUID().toString();
		var body = line(juice.productId(), 1, null, false, juice.small());

		assertThat(mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.put()
			.uri(TICKETS + "/" + ticket + "/lines/" + line)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.delete().uri(TICKETS + "/" + ticket + "/lines/" + line).exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post()
			.uri(TICKETS + "/" + ticket + "/repeat")
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"lineIds\":[\"%s\"]}".formatted(line))
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// helpers

	private MvcTestResult addLine(String body, Role role) {
		return addLine(body, UUID.randomUUID(), role, UUID.randomUUID());
	}

	private MvcTestResult addLine(String body, UUID actor, Role role, UUID key) {
		return mvc.post()
			.uri(TICKETS + "/" + ticket + "/lines")
			.with(AuthenticatedAs.user(actor, role))
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private MvcTestResult changeLine(String line, String body, Role role) {
		return mvc.put()
			.uri(TICKETS + "/" + ticket + "/lines/" + line)
			.with(as(role))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private MvcTestResult deleteLine(String line, Role role) {
		return mvc.delete().uri(TICKETS + "/" + ticket + "/lines/" + line).with(as(role)).exchange();
	}

	private MvcTestResult repeat(String body, Role role) {
		return repeat(body, UUID.randomUUID(), role, UUID.randomUUID());
	}

	private MvcTestResult repeat(String body, UUID actor, Role role, UUID key) {
		return mvc.post()
			.uri(TICKETS + "/" + ticket + "/repeat")
			.with(AuthenticatedAs.user(actor, role))
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private void markSent(String line) {
		jdbc.sql("update instore.ticket_line set state = 'SENT', board_source_id = ?, sent_at = now() where id = ?")
			.param(ticket)
			.param(UUID.fromString(line))
			.update();
	}

	private static String line(UUID product, int quantity, String note, boolean allergy, UUID... options) {
		var noteJson = note == null ? "null" : "\"" + note + "\"";
		var ids = new StringBuilder();
		for (var option : options) {
			ids.append(ids.isEmpty() ? "" : ",").append('"').append(option).append('"');
		}
		return "{\"productId\":\"%s\",\"modifierOptionIds\":[%s],\"quantity\":%d,\"note\":%s,\"allergy\":%s}"
			.formatted(product, ids, quantity, noteJson, allergy);
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static String lineIdOf(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return assertThat(result).bodyJson().extractingPath("$.lines[-1:].id").asList().actual().get(0).toString();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

	private static RequestPostProcessor as(Role role) {
		return AuthenticatedAs.user(UUID.randomUUID(), role);
	}

}
