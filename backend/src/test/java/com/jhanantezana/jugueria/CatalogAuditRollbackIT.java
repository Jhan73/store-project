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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.audit.internal.AuditLog;
import com.jhanantezana.jugueria.audit.internal.AuditLogRepository;
import com.jhanantezana.jugueria.catalog.internal.ProductImageStorage;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.CatalogTables;

// Same guarantee as StoreAuditRollbackIT for every audited catalog command: no change commits without its audit row.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogAuditRollbackIT {

	static final String PRODUCTS = "/api/v1/admin/products";

	static final String GROUPS = "/api/v1/admin/modifier-groups";

	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4 };

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	AuditLogRepository auditLogs;

	@MockitoBean
	ProductImageStorage storage;

	@BeforeEach
	void auditWritesSucceedWhileSettingUp() {
		reset(auditLogs, storage);
	}

	@AfterEach
	void cleanUp() {
		CatalogTables.clean(jdbc);
	}

	@Test
	void anAuditWriteFailureRollsBackACategoryCreation() {
		auditWritesNowFail();
		var name = "Cat-" + UUID.randomUUID();

		var result = mvc.post()
			.uri("/api/v1/admin/categories")
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\",\"displayOrder\":0}".formatted(name))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(count("select count(*) from catalog.category where name = ?", name)).isZero();
	}

	@Test
	void anAuditWriteFailureRollsBackAStationCreation() {
		auditWritesNowFail();
		var name = "Bar-" + UUID.randomUUID();

		var result = mvc.post()
			.uri("/api/v1/admin/stations")
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\"}".formatted(name))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(count("select count(*) from catalog.station where name = ?", name)).isZero();
	}

	@Test
	void anAuditWriteFailureRollsBackAProductCreation() {
		var categoryId = createCategory();
		auditWritesNowFail();
		var name = "Mango-" + UUID.randomUUID();

		var result = mvc.post().uri(PRODUCTS).with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "%s", "categoryId": "%s", "price": { "amount": "5.00", "currency": "PEN" },
				  "displayOrder": 0, "quickSalePinned": false }
				""".formatted(name, categoryId)).exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(count("select count(*) from catalog.product where name = ?", name)).isZero();
	}

	@Test
	void anAuditWriteFailureRollsBackAPriceChange() {
		var product = createProduct();
		auditWritesNowFail();

		var result = mvc.put()
			.uri(PRODUCTS + "/" + idOf(product))
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(product))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "name": "Mango repriced", "categoryId": "%s", "price": { "amount": "99.00", "currency": "PEN" },
					  "displayOrder": 0, "quickSalePinned": false }
					""".formatted(jsonCategoryOf(product)))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(jdbc.sql("select price_amount from catalog.product where id = ?")
			.param(UUID.fromString(idOf(product)))
			.query(java.math.BigDecimal.class)
			.single()).isEqualByComparingTo("5.00");
	}

	@Test
	void anAuditWriteFailureRollsBackADeactivation() {
		var product = createProduct();
		auditWritesNowFail();

		var result = mvc.post()
			.uri(PRODUCTS + "/" + idOf(product) + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(product))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(jdbc.sql("select active from catalog.product where id = ?")
			.param(UUID.fromString(idOf(product)))
			.query(Boolean.class)
			.single()).isTrue();
	}

	@Test
	void anAuditWriteFailureRollsBackAnImageChange() {
		var product = createProduct();
		auditWritesNowFail();

		var result = mvc.put()
			.multipart()
			.uri(PRODUCTS + "/" + idOf(product) + "/image")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(product))
			.file(new MockMultipartFile("file", "a.png", "image/png", PNG))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(jdbc.sql("select image_key from catalog.product where id = ?")
			.param(UUID.fromString(idOf(product)))
			.query(String.class)
			.optional()).isEmpty();
	}

	@Test
	void anAuditWriteFailureRollsBackAnAvailabilityChange() {
		var product = createProduct();
		auditWritesNowFail();

		var result = mvc.put()
			.uri("/api/v1/catalog/products/" + idOf(product) + "/availability")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":false}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(jdbc.sql("select available from catalog.product where id = ?")
			.param(UUID.fromString(idOf(product)))
			.query(Boolean.class)
			.single()).isTrue();
	}

	@Test
	void anAuditWriteFailureRollsBackAnOptionAvailabilityChange() {
		var group = createGroup("Boosters-" + UUID.randomUUID());
		var optionId = assertThat(group).bodyJson().extractingPath("$.options[0].id").actual().toString();
		auditWritesNowFail();

		var result = mvc.put()
			.uri("/api/v1/catalog/modifier-options/" + optionId + "/availability")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":false}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(jdbc.sql("select available from catalog.modifier_option where id = ?")
			.param(UUID.fromString(optionId))
			.query(Boolean.class)
			.single()).isTrue();
	}

	@Test
	void anAuditWriteFailureRollsBackAModifierGroupCreation() {
		auditWritesNowFail();
		var name = "Size-" + UUID.randomUUID();

		var result = mvc.post().uri(GROUPS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(groupJson(name)).exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(count("select count(*) from catalog.modifier_group where name = ?", name)).isZero();
	}

	@Test
	void anAuditWriteFailureRollsBackAModifierGroupUpdate() {
		var name = "Size-" + UUID.randomUUID();
		var group = createGroup(name);
		auditWritesNowFail();

		var result = mvc.put()
			.uri(GROUPS + "/" + idOf(group))
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(group))
			.contentType(MediaType.APPLICATION_JSON)
			.content(groupJson("Renamed-" + UUID.randomUUID()))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(count("select count(*) from catalog.modifier_group where name = ?", name)).isEqualTo(1);
	}

	@Test
	void anAuditWriteFailureRollsBackAModifierGroupDeletion() {
		var name = "Size-" + UUID.randomUUID();
		var group = createGroup(name);
		auditWritesNowFail();

		var result = mvc.delete()
			.uri(GROUPS + "/" + idOf(group))
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(group))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(count("select count(*) from catalog.modifier_group where name = ?", name)).isEqualTo(1);
		assertThat(count("select count(*) from catalog.modifier_option where group_id = ?",
				UUID.fromString(idOf(group)))).isEqualTo(1);
	}

	private void auditWritesNowFail() {
		when(auditLogs.save(any(AuditLog.class))).thenThrow(new RuntimeException("boom"));
	}

	private long count(String sql, Object param) {
		return jdbc.sql(sql).param(param).query(Long.class).single();
	}

	private String createCategory() {
		var category = mvc.post()
			.uri("/api/v1/admin/categories")
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Cat-%s\",\"displayOrder\":0}".formatted(UUID.randomUUID()))
			.exchange();
		return idOf(category);
	}

	private MvcTestResult createProduct() {
		return mvc.post().uri(PRODUCTS).with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "Mango-%s", "categoryId": "%s", "price": { "amount": "5.00", "currency": "PEN" },
				  "displayOrder": 0, "quickSalePinned": false }
				""".formatted(UUID.randomUUID(), createCategory())).exchange();
	}

	private MvcTestResult createGroup(String name) {
		return mvc.post().uri(GROUPS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(groupJson(name)).exchange();
	}

	private static String groupJson(String name) {
		return """
				{ "name": "%s", "required": false, "minChoices": 0, "maxChoices": 1,
				  "options": [ { "name": "A", "priceDelta": { "amount": "0.00", "currency": "PEN" } } ] }
				""".formatted(name);
	}

	private static String jsonCategoryOf(MvcTestResult product) {
		return assertThat(product).bodyJson().extractingPath("$.categoryId").actual().toString();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static String etagOf(MvcTestResult result) {
		return result.getResponse().getHeader(HttpHeaders.ETAG);
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
