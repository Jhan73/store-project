package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.audit.internal.AuditLog;
import com.jhanantezana.jugueria.audit.internal.AuditLogRepository;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

// Same guarantee as StoreAuditRollbackIT for every audited instore command: no change commits without its audit row.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InstoreAuditRollbackIT {

	static final String TABLES = "/api/v1/admin/tables";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	AuditLogRepository auditLogs;

	@BeforeEach
	void auditWritesSucceedWhileSettingUp() {
		reset(auditLogs);
	}

	@AfterEach
	void cleanUp() {
		InstoreTables.clean(jdbc);
	}

	@Test
	void anAuditWriteFailureRollsBackATableCreation() {
		auditWritesNowFail();
		var name = "Mesa-" + UUID.randomUUID();

		var result = mvc.post()
			.uri(TABLES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\"}".formatted(name))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(count("select count(*) from instore.dining_table where name = ?", name)).isZero();
	}

	@Test
	void anAuditWriteFailureRollsBackATableUpdate() {
		var table = createTable();
		auditWritesNowFail();

		var result = mvc.put()
			.uri(TABLES + "/" + idOf(table))
			.with(admin())
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Renamed-%s\",\"displayOrder\":9}".formatted(UUID.randomUUID()))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(jdbc.sql("select display_order from instore.dining_table where id = ?")
			.param(UUID.fromString(idOf(table)))
			.query(Integer.class)
			.single()).isZero();
	}

	@Test
	void anAuditWriteFailureRollsBackATableDeactivation() {
		var table = createTable();
		auditWritesNowFail();

		var result = mvc.post()
			.uri(TABLES + "/" + idOf(table) + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(isActive(idOf(table))).isTrue();
	}

	@Test
	void anAuditWriteFailureRollsBackATableReactivation() {
		var table = createTable();
		jdbc.sql("update instore.dining_table set active = false where id = ?")
			.param(UUID.fromString(idOf(table)))
			.update();
		auditWritesNowFail();

		var result = mvc.post()
			.uri(TABLES + "/" + idOf(table) + "/reactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(isActive(idOf(table))).isFalse();
	}

	private void auditWritesNowFail() {
		when(auditLogs.save(any(AuditLog.class))).thenThrow(new RuntimeException("boom"));
	}

	private MvcTestResult createTable() {
		var result = mvc.post()
			.uri(TABLES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Mesa-%s\",\"displayOrder\":0}".formatted(UUID.randomUUID()))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return result;
	}

	private boolean isActive(String id) {
		return jdbc.sql("select active from instore.dining_table where id = ?")
			.param(UUID.fromString(id))
			.query(Boolean.class)
			.single();
	}

	private long count(String sql, Object param) {
		return jdbc.sql(sql).param(param).query(Long.class).single();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
