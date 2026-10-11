package com.jhanantezana.jugueria.instore.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TableGridControllerIT {

	static final String GRID = "/api/v1/tables";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		InstoreTables.clean(jdbc);
	}

	@Test
	void anEmptyGridIsAnEmptyList() {
		var result = mvc.get().uri(GRID).with(as(Role.SERVER)).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$").asList().isEmpty();
	}

	@Test
	void returnsEveryActiveTableAsFreeInDisplayOrder() {
		var second = createTable("B-" + UUID.randomUUID(), null, 2);
		var first = createTable("A-" + UUID.randomUUID(), "Terrace", 1);

		var result = mvc.get().uri(GRID).with(as(Role.SERVER)).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$[*].id").asList().containsExactly(first, second);
		assertThat(result).bodyJson().extractingPath("$[*].status").asList().containsExactly("FREE", "FREE");
		assertThat(result).bodyJson().extractingPath("$[0].area").isEqualTo("Terrace");
		assertThat(result).bodyJson().extractingPath("$[1].area").isNull();
		assertThat(result).bodyJson().extractingPath("$[0].ticketOpenedAt").isNull();
		assertThat(result).bodyJson().extractingPath("$[0].displayOrder").isEqualTo(1);
	}

	@Test
	void leavesDeactivatedTablesOffTheGridAndBringsThemBackOnReactivation() {
		var kept = createTable("Kept-" + UUID.randomUUID(), null, 1);
		var retired = createTable("Retired-" + UUID.randomUUID(), null, 2);
		setActive(retired, "deactivate");

		var without = mvc.get().uri(GRID).with(as(Role.CASHIER)).exchange();
		assertThat(without).bodyJson().extractingPath("$[*].id").asList().containsExactly(kept);

		setActive(retired, "reactivate");

		var with = mvc.get().uri(GRID).with(as(Role.CASHIER)).exchange();
		assertThat(with).bodyJson().extractingPath("$[*].id").asList().containsExactly(kept, retired);
	}

	@Test
	void showsATableWithAnOpenTicketAsOccupiedAndWhenItsTicketWasOpened() {
		var busy = InstoreTables.aTable(jdbc, "A-" + UUID.randomUUID());
		var free = InstoreTables.aTable(jdbc, "B-" + UUID.randomUUID());
		var ticket = UUID.randomUUID();
		jdbc.sql("""
				insert into instore.ticket (id, type, table_id, status, currency, opened_by, created_at, updated_at)
				values (?, 'TABLE', ?, 'OPEN', 'PEN', ?, '2026-10-11T10:00:00Z', now())
				""").param(ticket).param(busy).param(UUID.randomUUID()).update();

		var result = mvc.get().uri(GRID).with(as(Role.SERVER)).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='" + busy + "')].status")
			.asList()
			.containsExactly("OCCUPIED");
		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='" + busy + "')].ticketOpenedAt")
			.asList()
			.containsExactly("2026-10-11T10:00:00Z");
		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='" + busy + "')].ticketId")
			.asList()
			.containsExactly(ticket.toString());
		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='" + free + "')].status")
			.asList()
			.containsExactly("FREE");
	}

	@Test
	void showsATableWhoseBillWasRequestedAsWaitingForPayment() {
		var table = InstoreTables.aTable(jdbc);
		jdbc.sql("""
				insert into instore.ticket (id, type, table_id, status, bill_requested, currency, opened_by, created_at,
					updated_at)
				values (?, 'TABLE', ?, 'OPEN', true, 'PEN', ?, now(), now())
				""").param(UUID.randomUUID()).param(table).param(UUID.randomUUID()).update();

		var result = mvc.get().uri(GRID).with(as(Role.SERVER)).exchange();

		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='" + table + "')].status")
			.asList()
			.containsExactly("WAITING_FOR_PAYMENT");
	}

	@Test
	void aClosedTicketLeavesTheTableFree() {
		var table = InstoreTables.aTable(jdbc);
		jdbc.sql("""
				insert into instore.ticket (id, type, table_id, status, currency, opened_by, closed_at, created_at, updated_at)
				values (?, 'TABLE', ?, 'CLOSED', 'PEN', ?, now(), now(), now())
				""").param(UUID.randomUUID()).param(table).param(UUID.randomUUID()).update();

		var result = mvc.get().uri(GRID).with(as(Role.SERVER)).exchange();

		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='" + table + "')].status")
			.asList()
			.containsExactly("FREE");
		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='" + table + "')].ticketId")
			.asList()
			.containsNull();
	}

	@Test
	void everyFloorRoleMayReadTheGrid() {
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.ADMIN }) {
			assertThat(mvc.get().uri(GRID).with(as(role)).exchange()).hasStatusOk();
		}
	}

	@Test
	void aCustomerMayNotReadTheGrid() {
		assertThat(mvc.get().uri(GRID).with(as(Role.CUSTOMER)).exchange()).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void anAnonymousCallerMayNotReadTheGrid() {
		assertThat(mvc.get().uri(GRID).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private String createTable(String name, String area, int displayOrder) {
		var areaJson = area == null ? "null" : "\"" + area + "\"";
		var result = mvc.post()
			.uri("/api/v1/admin/tables")
			.with(as(Role.ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\",\"area\":%s,\"displayOrder\":%d}".formatted(name, areaJson, displayOrder))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private void setActive(String id, String action) {
		var etag = "\"" + jdbc.sql("select version from instore.dining_table where id = ?")
			.param(UUID.fromString(id))
			.query(Long.class)
			.single() + "\"";
		MvcTestResult result = mvc.post()
			.uri("/api/v1/admin/tables/" + id + "/" + action)
			.with(as(Role.ADMIN))
			.header(HttpHeaders.IF_MATCH, etag)
			.exchange();
		assertThat(result).hasStatusOk();
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor as(Role role) {
		return AuthenticatedAs.user(UUID.randomUUID(), role);
	}

}
