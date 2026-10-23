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
import com.jhanantezana.testsupport.PreparationTables;

/** Renaming a table relabels the cards of its open ticket on the board in the same step. */
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TableRenameBoardIT {

	static final String TICKETS = "/api/v1/tickets";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	UUID table;

	InstoreTables.SizedProduct juice;

	@BeforeEach
	void aTableWithAnOpenTicket() {
		juice = InstoreTables.aSizedProduct(jdbc);
		table = InstoreTables.aTable(jdbc, "Table 4");
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.cleanAll(jdbc);
	}

	@Test
	void relabelsTheCardOfTheOpenTicket() {
		var ticket = openAt(table);
		addLine(ticket);
		send(ticket);

		var result = rename(table, "Terrace 1", "0");

		assertThat(result).hasStatusOk();
		assertThat(labels()).containsExactly("Terrace 1");
	}

	@Test
	void relabelsEveryRoundOfTheTicket() {
		var ticket = openAt(table);
		addLine(ticket);
		send(ticket);
		PreparationTables.forceStatus(jdbc, cardOf(ticket), "HANDED_OVER");
		addLine(ticket);
		send(ticket);

		rename(table, "Terrace 1", "0");

		assertThat(labels()).containsExactly("Terrace 1", "Terrace 1");
	}

	@Test
	void aTicketSentAfterTheRenameUsesTheNewName() {
		var ticket = openAt(table);
		rename(table, "Terrace 1", "0");
		addLine(ticket);

		send(ticket);

		assertThat(labels()).containsExactly("Terrace 1");
	}

	@Test
	void leavesTheCardsOfAClosedTicketAlone() {
		var ticket = openAt(table);
		addLine(ticket);
		send(ticket);
		jdbc.sql("update instore.ticket set status = 'CLOSED', closed_at = now() where id = ?").param(ticket).update();

		var result = rename(table, "Terrace 1", "0");

		assertThat(result).hasStatusOk();
		assertThat(labels()).containsExactly("Table 4");
	}

	@Test
	void renamesATableThatHasNoTicket() {
		assertThat(rename(table, "Terrace 1", "0")).hasStatusOk();
	}

	@Test
	void changingOnlyTheAreaTouchesNoCard() {
		var ticket = openAt(table);
		addLine(ticket);
		send(ticket);

		var result = mvc.put()
			.uri("/api/v1/admin/tables/" + table)
			.with(as(Role.ADMIN))
			.header("If-Match", "\"0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Table 4\",\"area\":\"Terrace\",\"displayOrder\":0}")
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(labels()).containsExactly("Table 4");
	}

	private MvcTestResult rename(UUID target, String name, String version) {
		return mvc.put()
			.uri("/api/v1/admin/tables/" + target)
			.with(as(Role.ADMIN))
			.header("If-Match", "\"" + version + "\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\",\"displayOrder\":0}".formatted(name))
			.exchange();
	}

	private java.util.List<String> labels() {
		return jdbc.sql("select label from preparation.board_order order by created_at, id").query(String.class).list();
	}

	private UUID openAt(UUID target) {
		var result = mvc.post()
			.uri(TICKETS)
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tableId\":\"%s\"}".formatted(target))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return UUID.fromString(assertThat(result).bodyJson().extractingPath("$.id").actual().toString());
	}

	private void addLine(UUID target) {
		var result = mvc.post()
			.uri(TICKETS + "/" + target + "/lines")
			.with(as(Role.SERVER))
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"productId\":\"%s\",\"modifierOptionIds\":[\"%s\"],\"quantity\":1,\"note\":null,\"allergy\":false}"
				.formatted(juice.productId(), juice.small()))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
	}

	private void send(UUID target) {
		assertThat(mvc.post().uri(TICKETS + "/" + target + "/send").with(as(Role.SERVER)).exchange()).hasStatusOk();
	}

	private UUID cardOf(UUID source) {
		return jdbc.sql("select id from preparation.board_order where source = 'TICKET' and source_id = ?")
			.param(source)
			.query(UUID.class)
			.single();
	}

	private static RequestPostProcessor as(Role role) {
		return AuthenticatedAs.user(UUID.randomUUID(), role);
	}

}
