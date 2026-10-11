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
import com.jhanantezana.testsupport.InventoryTables;

// Same guarantee as InstoreAuditRollbackIT for every audited stock command: no change commits without its audit row.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InventoryAuditRollbackIT {

	static final String STOCK = "/api/v1/admin/stock-items";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	AuditLogRepository auditLogs;

	@BeforeEach
	void auditWritesSucceedWhileSettingUp() {
		reset(auditLogs);
		InventoryTables.anAdjustmentReason(jdbc, "Damaged");
	}

	@AfterEach
	void cleanUp() {
		InventoryTables.clean(jdbc);
	}

	@Test
	void anAuditWriteFailureRollsBackStartingToTrackAProduct() {
		var product = InventoryTables.aProduct(jdbc);
		auditWritesNowFail();

		var result = mvc.put()
			.uri(STOCK + "/" + product)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tracked\":true,\"lowStockThreshold\":2}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(count("select count(*) from inventory.stock_item where product_id = ?", product)).isZero();
		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
	}

	@Test
	void anAuditWriteFailureRollsBackAnEntryAndFreesItsKey() {
		var product = trackedWithStock(3);
		var key = UUID.randomUUID();
		auditWritesNowFail();

		var result = post(product, "entries", key, "{\"quantity\":4}");

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(onHand(product)).isEqualTo(3);
		assertThat(count("select count(*) from inventory.stock_movement where product_id = ? and type = 'ENTRY'",
				product)).isEqualTo(1);
		reset(auditLogs);
		assertThat(post(product, "entries", key, "{\"quantity\":4}")).hasStatusOk();
		assertThat(onHand(product)).isEqualTo(7);
	}

	@Test
	void anAuditWriteFailureRollsBackAnAdjustment() {
		var product = trackedWithStock(3);
		auditWritesNowFail();

		var result = post(product, "adjustments", UUID.randomUUID(), "{\"quantityDelta\":-2,\"reason\":\"Damaged\"}");

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(onHand(product)).isEqualTo(3);
		assertThat(count("select count(*) from inventory.stock_movement where product_id = ? and type = 'ADJUSTMENT'",
				product)).isZero();
	}

	private UUID trackedWithStock(int quantity) {
		var product = InventoryTables.aProduct(jdbc);
		assertThat(mvc.put()
			.uri(STOCK + "/" + product)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"tracked\":true,\"lowStockThreshold\":0}")
			.exchange()).hasStatusOk();
		assertThat(post(product, "entries", UUID.randomUUID(), "{\"quantity\":%d}".formatted(quantity)))
			.hasStatusOk();
		return product;
	}

	private MvcTestResult post(UUID product, String action, UUID key, String body) {
		return mvc.post()
			.uri(STOCK + "/" + product + "/" + action)
			.with(admin())
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private void auditWritesNowFail() {
		when(auditLogs.save(any(AuditLog.class))).thenThrow(new RuntimeException("boom"));
	}

	private int onHand(UUID product) {
		return jdbc.sql("select on_hand from inventory.stock_item where product_id = ?")
			.param(product)
			.query(Integer.class)
			.single();
	}

	private long count(String sql, Object param) {
		return jdbc.sql(sql).param(param).query(Long.class).single();
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
