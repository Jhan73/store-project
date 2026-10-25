package com.jhanantezana.jugueria.instore.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.instore.TableDeleted;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class TableDeleteIT {

	static final String TABLES = "/api/v1/admin/tables";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@AfterEach
	void cleanUp() {
		InstoreTables.clean(jdbc);
	}

	@Test
	void deletesATableThatNeverHadATicketAndPublishesItsSnapshot() {
		var table = InstoreTables.aTable(jdbc, "Mesa-" + UUID.randomUUID());

		var result = delete(table, "\"0\"");

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(count(table)).isZero();
		assertThat(events.stream(TableDeleted.class)).singleElement().satisfies(event -> {
			assertThat(event.tableId()).isEqualTo(table);
			assertThat(event.before().name()).startsWith("Mesa-");
		});
	}

	@Test
	void anInactiveTableWithNoTicketCanBeDeletedToo() {
		var table = InstoreTables.aTable(jdbc);
		InstoreTables.deactivate(jdbc, table);

		assertThat(delete(table, "\"0\"")).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void refusesToDeleteATableThatHadAClosedTicket() {
		var table = InstoreTables.aTable(jdbc);
		aTicketOn(table, "CLOSED");

		var result = delete(table, "\"0\"");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("instore.table-in-use");
		assertThat(count(table)).isEqualTo(1);
		assertThat(events.stream(TableDeleted.class)).isEmpty();
	}

	@Test
	void refusesToDeleteATableWithAnOpenTicket() {
		var table = InstoreTables.aTable(jdbc);
		aTicketOn(table, "OPEN");

		assertThat(delete(table, "\"0\"")).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void theListSaysWhichTablesCanBeDeleted() {
		var free = InstoreTables.aTable(jdbc);
		var used = InstoreTables.aTable(jdbc);
		aTicketOn(used, "CLOSED");

		var result = mvc.get().uri(TABLES).with(admin()).exchange();

		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='%s')].deletable".formatted(free))
			.asList()
			.containsExactly(true);
		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='%s')].deletable".formatted(used))
			.asList()
			.containsExactly(false);
	}

	@Test
	void aNewTableSaysItCanBeDeleted() {
		var result = mvc.post()
			.uri(TABLES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Mesa-%s\"}".formatted(UUID.randomUUID()))
			.exchange();

		assertThat(result).bodyJson().extractingPath("$.deletable").isEqualTo(true);
	}

	@Test
	void aStaleOrMissingIfMatchIsRejectedAndAnUnknownTableIsNotFound() {
		var table = InstoreTables.aTable(jdbc);

		assertThat(mvc.delete().uri(TABLES + "/" + table).with(admin()).exchange())
			.hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(delete(table, "\"5\"")).hasStatus(HttpStatus.PRECONDITION_FAILED);
		var unknown = delete(UUID.randomUUID(), "\"0\"");
		assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("instore.table-not-found");
		assertThat(count(table)).isEqualTo(1);
	}

	@Test
	void onlyAnAdminMayDelete() {
		var table = InstoreTables.aTable(jdbc);

		var result = mvc.delete()
			.uri(TABLES + "/" + table)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	private void aTicketOn(UUID table, String status) {
		var now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbc.sql("""
				insert into instore.ticket (id, type, table_id, status, currency, opened_by, created_at, updated_at)
				values (:id, 'TABLE', :table, :status, 'PEN', :user, :now, :now)
				""")
			.param("id", UUID.randomUUID())
			.param("table", table)
			.param("status", status)
			.param("user", UUID.randomUUID())
			.param("now", now)
			.update();
	}

	private long count(UUID table) {
		return jdbc.sql("select count(*) from instore.dining_table where id = ?").param(table).query(Long.class).single();
	}

	private MvcTestResult delete(UUID id, String etag) {
		return mvc.delete().uri(TABLES + "/" + id).with(admin()).header(HttpHeaders.IF_MATCH, etag).exchange();
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
