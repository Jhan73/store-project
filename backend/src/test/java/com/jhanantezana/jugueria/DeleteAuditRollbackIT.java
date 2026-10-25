package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import com.jhanantezana.jugueria.identity.internal.UserAccount;
import com.jhanantezana.jugueria.identity.internal.UserAccountRepository;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;
import com.jhanantezana.testsupport.InventoryTables;

// Same guarantee as the other audit rollback tests for every delete and for retiring a station: nothing commits
// without its audit row.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DeleteAuditRollbackIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	UserAccountRepository accounts;

	@MockitoBean
	AuditLogRepository auditLogs;

	@BeforeEach
	void auditWritesSucceedWhileSettingUp() {
		reset(auditLogs);
	}

	@AfterEach
	void cleanUp() {
		jdbc.sql("delete from identity.set_password_token").update();
		accounts.deleteAll();
		jdbc.sql("delete from store.delivery_zone").update();
		InstoreTables.clean(jdbc);
		InventoryTables.clean(jdbc);
	}

	@Test
	void anAuditWriteFailureRollsBackAProductDelete() {
		var product = InventoryTables.aProduct(jdbc);
		auditWritesNowFail();

		assertThat(delete("/api/v1/admin/products/" + product)).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

		assertThat(count("select count(*) from catalog.product where id = ?", product)).isEqualTo(1);
	}

	@Test
	void anAuditWriteFailureRollsBackACategoryDelete() {
		var category = jdbc.sql("insert into catalog.category (id, station_id, name, display_order, active, created_at, "
				+ "updated_at) select ?, id, ?, 0, true, now(), now() from catalog.station where default_station "
				+ "returning id").param(UUID.randomUUID()).param("Cat-" + UUID.randomUUID()).query(UUID.class).single();
		auditWritesNowFail();

		assertThat(delete("/api/v1/admin/categories/" + category)).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

		assertThat(count("select count(*) from catalog.category where id = ?", category)).isEqualTo(1);
	}

	@Test
	void anAuditWriteFailureRollsBackATableDelete() {
		var table = InstoreTables.aTable(jdbc);
		auditWritesNowFail();

		assertThat(delete("/api/v1/admin/tables/" + table)).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

		assertThat(count("select count(*) from instore.dining_table where id = ?", table)).isEqualTo(1);
	}

	@Test
	void anAuditWriteFailureRollsBackADeliveryZoneDelete() {
		var zone = UUID.randomUUID();
		jdbc.sql("""
				insert into store.delivery_zone (id, name, fee_amount, fee_currency, delivery_minutes, active, created_at,
				    updated_at)
				values (?, ?, 5.00, 'PEN', 20, true, now(), now())
				""").param(zone).param("Zone-" + zone).update();
		auditWritesNowFail();

		assertThat(delete("/api/v1/admin/delivery-zones/" + zone)).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

		assertThat(count("select count(*) from store.delivery_zone where id = ?", zone)).isEqualTo(1);
	}

	@Test
	void anAuditWriteFailureRollsBackAStaffDelete() {
		var admin = new UserAccount("admin@jugueria.pe", "hash", Role.ADMIN, Instant.now(), true);
		accounts.save(admin);
		var invited = accounts.save(new UserAccount("maria@jugueria.pe", "hash", Role.CASHIER, Instant.now(), true));
		auditWritesNowFail();

		var result = mvc.delete().uri("/api/v1/staff/" + invited.getId()).with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(accounts.findById(invited.getId())).isPresent();
	}

	@Test
	void anAuditWriteFailureRollsBackAStationDeactivation() {
		var station = UUID.randomUUID();
		jdbc.sql("insert into catalog.station (id, name, default_station, active, created_at, updated_at) "
				+ "values (?, ?, false, true, now(), now())").param(station).param("Bar-" + station).update();
		auditWritesNowFail();

		var result = mvc.post()
			.uri("/api/v1/admin/stations/" + station + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(jdbc.sql("select active from catalog.station where id = ?")
			.param(station)
			.query(Boolean.class)
			.single()).isTrue();
	}

	private MvcTestResult delete(String uri) {
		return mvc.delete().uri(uri).with(admin()).header(HttpHeaders.IF_MATCH, "\"0\"").exchange();
	}

	private void auditWritesNowFail() {
		when(auditLogs.save(any(AuditLog.class))).thenThrow(new RuntimeException("boom"));
	}

	private long count(String sql, UUID id) {
		return jdbc.sql(sql).param(id).query(Long.class).single();
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
