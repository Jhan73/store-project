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

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

/** The paged open list, the closed-ticket search and the editable ticket note. */
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TicketListsIT {

	static final String TICKETS = "/api/v1/tickets";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	UUID cashier = UUID.randomUUID();

	UUID otherCashier = UUID.randomUUID();

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void aProduct() {
		juice = InstoreTables.aSizedProduct(jdbc);
		InstoreTables.aVoidReason(jdbc, "Changed mind");
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	// open list

	@Test
	void theOpenListIsPagedOldestFirst() {
		var ids = new java.util.ArrayList<String>();
		for (var i = 0; i < 3; i++) {
			ids.add(idOf(open("{\"counterName\":\"Guest " + i + "\"}")));
		}

		var first = get(TICKETS + "?page=0&size=2", Role.SERVER);
		var second = get(TICKETS + "?page=1&size=2", Role.SERVER);

		assertThat(first).hasStatusOk();
		assertThat(first).bodyJson().extractingPath("$.content[*].id").asList().containsExactly(ids.get(0), ids.get(1));
		assertThat(first).bodyJson().extractingPath("$.totalElements").isEqualTo(3);
		assertThat(first).bodyJson().extractingPath("$.totalPages").isEqualTo(2);
		assertThat(second).bodyJson().extractingPath("$.content[*].id").asList().containsExactly(ids.get(2));
	}

	@Test
	void theOpenListDefaultsToTheLargestPage() {
		open("{\"counterName\":\"Ana\"}");

		var result = get(TICKETS, Role.SERVER);

		assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(100);
	}

	// note

	@Test
	void changesAndClearsTheNoteOfAnOpenTicket() {
		var ticket = idOf(open("{\"counterName\":\"Ana\",\"note\":\"Window seat\"}"));

		var changed = putNote(ticket, "{\"note\":\"  Birthday  \"}", Role.SERVER);
		var cleared = putNote(ticket, "{\"note\":null}", Role.CASHIER);

		assertThat(changed).hasStatusOk();
		assertThat(changed).bodyJson().extractingPath("$.note").isEqualTo("Birthday");
		assertThat(cleared).hasStatusOk();
		assertThat(cleared).bodyJson().extractingPath("$.note").isNull();
	}

	@Test
	void rejectsATooLongNoteAnUnknownAndAClosedTicket() {
		var ticket = idOf(open("{\"counterName\":\"Ana\"}"));

		assertThat(putNote(ticket, "{\"note\":\"" + "x".repeat(141) + "\"}", Role.SERVER)).hasStatus(HttpStatus.BAD_REQUEST);
		assertProblem(putNote(UUID.randomUUID().toString(), "{\"note\":\"a\"}", Role.SERVER), HttpStatus.NOT_FOUND,
				"instore.ticket-not-found");
		close(UUID.fromString(ticket));
		assertProblem(putNote(ticket, "{\"note\":\"a\"}", Role.SERVER), HttpStatus.CONFLICT, "instore.ticket-closed");
	}

	@Test
	void everyFloorRoleMayChangeTheNoteAndACustomerMayNot() {
		var ticket = idOf(open("{\"counterName\":\"Ana\"}"));

		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.ADMIN }) {
			assertThat(putNote(ticket, "{\"note\":\"a\"}", role)).hasStatusOk();
		}
		assertThat(putNote(ticket, "{\"note\":\"a\"}", Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
	}

	// closed list

	@Test
	void listsClosedTicketsNewestFirstAndLeavesOutTheOpenAndCancelledOnes() {
		var older = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		var newer = UUID.fromString(idOf(open("{\"counterName\":\"Luis\"}")));
		var stillOpen = idOf(open("{\"counterName\":\"Eva\"}"));
		var cancelled = idOf(open("{\"counterName\":\"Zoe\"}"));
		mvc.post().uri(TICKETS + "/" + cancelled + "/cancel").with(as(Role.SERVER)).exchange();
		close(older, cashier, "2026-03-10T15:00:00Z", 2);
		close(newer, cashier, "2026-03-11T15:00:00Z", 1);

		var result = get(TICKETS + "/closed", Role.CASHIER);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.content[*].id").asList().containsExactly(newer.toString(), older.toString());
		assertThat(result).bodyJson().extractingPath("$.content[0].total.amount").isEqualTo("5.00");
		assertThat(result).bodyJson().extractingPath("$.content[1].total.amount").isEqualTo("10.00");
		assertThat(result).bodyJson().extractingPath("$.content[0].cashierId").isEqualTo(cashier.toString());
		assertThat(result).bodyJson().extractingPath("$.content[0].voided").isEqualTo(false);
		assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
		assertThat(stillOpen).isNotNull();
	}

	@Test
	void filtersByTheStoreDayTheTicketClosedOn() {
		var lateNight = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		var nextDay = UUID.fromString(idOf(open("{\"counterName\":\"Luis\"}")));
		// 03:00 UTC is still the 9th in Lima (UTC-5).
		close(lateNight, cashier, "2026-03-10T03:00:00Z", 1);
		close(nextDay, cashier, "2026-03-11T15:00:00Z", 1);

		var ninth = get(TICKETS + "/closed?from=2026-03-09&to=2026-03-09", Role.ADMIN);
		var fromTenth = get(TICKETS + "/closed?from=2026-03-10", Role.ADMIN);
		var upToTenth = get(TICKETS + "/closed?to=2026-03-10", Role.ADMIN);

		assertThat(ninth).bodyJson().extractingPath("$.content[*].id").asList().containsExactly(lateNight.toString());
		assertThat(fromTenth).bodyJson().extractingPath("$.content[*].id").asList().containsExactly(nextDay.toString());
		assertThat(upToTenth).bodyJson().extractingPath("$.content[*].id").asList().containsExactly(lateNight.toString());
	}

	@Test
	void filtersByCashierTableAndChannel() {
		var table = InstoreTables.aTable(jdbc, "Table 4");
		var counter = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		var seated = UUID.fromString(idOf(open("{\"tableId\":\"%s\"}".formatted(table))));
		close(counter, cashier, "2026-03-10T15:00:00Z", 1);
		close(seated, otherCashier, "2026-03-10T16:00:00Z", 1);

		assertThat(get(TICKETS + "/closed?cashierId=" + otherCashier, Role.ADMIN)).bodyJson()
			.extractingPath("$.content[*].id")
			.asList()
			.containsExactly(seated.toString());
		assertThat(get(TICKETS + "/closed?tableId=" + table, Role.ADMIN)).bodyJson()
			.extractingPath("$.content[*].id")
			.asList()
			.containsExactly(seated.toString());
		assertThat(get(TICKETS + "/closed?type=COUNTER", Role.ADMIN)).bodyJson()
			.extractingPath("$.content[*].id")
			.asList()
			.containsExactly(counter.toString());
		assertThat(get(TICKETS + "/closed?type=TABLE&cashierId=" + cashier, Role.ADMIN)).bodyJson()
			.extractingPath("$.totalElements")
			.isEqualTo(0);
	}

	@Test
	void aVoidedPaymentDoesNotMakeItsRecorderTheCashierOfTheTicket() {
		var ticket = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		close(ticket, cashier, "2026-03-10T15:00:00Z", 1);
		var voidedPayment = jdbc.sql("select id from instore.in_store_payment where ticket_id = ?")
			.param(ticket)
			.query(UUID.class)
			.single();
		jdbc.sql("""
				insert into instore.payment_void (id, payment_id, ticket_id, reason_code, voided_by, voided_at)
				values (?, ?, ?, 'Changed mind', ?, ?)
				""")
			.param(UUID.randomUUID())
			.param(voidedPayment)
			.param(ticket)
			.param(cashier)
			.param(java.time.OffsetDateTime.parse("2026-03-10T15:30:00Z"))
			.update();
		jdbc.sql("""
				insert into instore.in_store_payment (id, ticket_id, shift_id, method, currency, paid_amount, change_amount,
					recorded_by, recorded_at)
				values (?, ?, ?, 'CARD', 'PEN', 5.00, 0, ?, ?)
				""")
			.param(UUID.randomUUID())
			.param(ticket)
			.param(UUID.randomUUID())
			.param(otherCashier)
			.param(java.time.OffsetDateTime.parse("2026-03-10T14:00:00Z"))
			.update();

		assertThat(get(TICKETS + "/closed", Role.ADMIN)).bodyJson()
			.extractingPath("$.content[0].cashierId")
			.isEqualTo(otherCashier.toString());
		assertThat(get(TICKETS + "/closed?cashierId=" + cashier, Role.ADMIN)).bodyJson()
			.extractingPath("$.totalElements")
			.isEqualTo(0);
		assertThat(get(TICKETS + "/closed?cashierId=" + otherCashier, Role.ADMIN)).bodyJson()
			.extractingPath("$.totalElements")
			.isEqualTo(1);
	}

	@Test
	void flagsAVoidedSaleAndPages() {
		var first = UUID.fromString(idOf(open("{\"counterName\":\"Ana\"}")));
		var second = UUID.fromString(idOf(open("{\"counterName\":\"Luis\"}")));
		close(first, cashier, "2026-03-10T15:00:00Z", 1);
		close(second, cashier, "2026-03-11T15:00:00Z", 1);
		jdbc.sql("""
				insert into instore.sale_void (id, ticket_id, reason_code, voided_by, voided_at)
				values (?, ?, 'Changed mind', ?, now())
				""").param(UUID.randomUUID()).param(first).param(cashier).update();

		var page = get(TICKETS + "/closed?size=1&page=1", Role.CASHIER);

		assertThat(page).bodyJson().extractingPath("$.content[0].id").isEqualTo(first.toString());
		assertThat(page).bodyJson().extractingPath("$.content[0].voided").isEqualTo(true);
		assertThat(page).bodyJson().extractingPath("$.totalPages").isEqualTo(2);
	}

	@Test
	void rejectsAReversedDateRange() {
		var result = get(TICKETS + "/closed?from=2026-03-11&to=2026-03-10", Role.ADMIN);

		assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT, "instore.invalid-date-range");
	}

	@Test
	void onlyRegisterStaffMaySearchClosedTickets() {
		assertThat(get(TICKETS + "/closed", Role.CASHIER)).hasStatusOk();
		assertThat(get(TICKETS + "/closed", Role.ADMIN)).hasStatusOk();
		assertThat(get(TICKETS + "/closed", Role.SERVER)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(get(TICKETS + "/closed", Role.CUSTOMER)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri(TICKETS + "/closed").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// helpers

	private MvcTestResult open(String body) {
		var result = mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return result;
	}

	/** Closes a ticket as a settled sale of {@code units} 5.00 juices, written straight to the tables. */
	private void close(UUID ticket, UUID by, String closedAt, int units) {
		var now = java.time.OffsetDateTime.parse(closedAt);
		var shift = UUID.randomUUID();
		for (var i = 0; i < units; i++) {
			jdbc.sql("""
					insert into instore.ticket_line (id, ticket_id, position, product_id, product_name, station_id, quantity,
						unit_price_amount, unit_price_currency, modifiers, state, board_source_id, sent_at, created_at, updated_at)
					select ?, ?, ?, ?, 'Juice', id, 1, 5.00, 'PEN', '[]', 'SENT', ?, ?, ?, ? from catalog.station limit 1
					""")
				.param(UUID.randomUUID())
				.param(ticket)
				.param(i)
				.param(juice.productId())
				.param(ticket)
				.param(now)
				.param(now)
				.param(now)
				.update();
		}
		jdbc.sql("""
				insert into instore.in_store_payment (id, ticket_id, shift_id, method, currency, paid_amount, change_amount,
					recorded_by, recorded_at)
				values (?, ?, ?, 'CARD', 'PEN', ?, 0, ?, ?)
				""").param(UUID.randomUUID()).param(ticket).param(shift).param(5.00 * units).param(by).param(now).update();
		jdbc.sql("update instore.ticket set status = 'CLOSED', closed_at = ? where id = ?").param(now).param(ticket).update();
	}

	private void close(UUID ticket) {
		jdbc.sql("update instore.ticket set status = 'CLOSED', closed_at = now() where id = ?").param(ticket).update();
	}

	private MvcTestResult get(String uri, Role role) {
		return mvc.get().uri(uri).with(as(role)).exchange();
	}

	private MvcTestResult putNote(String ticket, String body, Role role) {
		return mvc.put()
			.uri(TICKETS + "/" + ticket + "/note")
			.with(as(role))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
		assertThat(result).hasStatus(status);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor as(Role role) {
		return AuthenticatedAs.user(UUID.randomUUID(), role);
	}

}
