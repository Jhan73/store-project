package com.jhanantezana.jugueria.audit.web;

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
import com.jhanantezana.testsupport.CatalogTables;

// The full chain: an admin HTTP command, the published event, the audit row, and the audit read endpoint.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditCatalogChainIT {

	static final String AUDIT_ENTRIES = "/api/v1/audit-entries";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		CatalogTables.clean(jdbc);
	}

	@Test
	void aCategoryCreatedOverHttpAppearsInTheAuditLog() {
		var actorId = UUID.randomUUID();
		var name = "Juices-" + UUID.randomUUID();

		var created = mvc.post()
			.uri("/api/v1/admin/categories")
			.with(admin(actorId))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\",\"displayOrder\":4}".formatted(name))
			.exchange();
		assertThat(created).hasStatus(HttpStatus.CREATED);
		var categoryId = idOf(created);

		var audit = mvc.get()
			.uri(AUDIT_ENTRIES + "?entityType=CATEGORY&entityId=" + categoryId)
			.with(admin(UUID.randomUUID()))
			.exchange();

		assertThat(audit).hasStatusOk();
		assertThat(audit).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
		assertThat(audit).bodyJson().extractingPath("$.content[0].action").isEqualTo("CATEGORY_CREATED");
		assertThat(audit).bodyJson().extractingPath("$.content[0].actorId").isEqualTo(actorId.toString());
		assertThat(audit).bodyJson().extractingPath("$.content[0].after.name").isEqualTo(name);
		assertThat(audit).bodyJson().extractingPath("$.content[0].after.displayOrder").isEqualTo(4);
	}

	@Test
	void aProductCreatedOverHttpAppearsInTheAuditLog() {
		var categoryId = idOf(mvc.post()
			.uri("/api/v1/admin/categories")
			.with(admin(UUID.randomUUID()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Cat-%s\",\"displayOrder\":0}".formatted(UUID.randomUUID()))
			.exchange());
		var name = "Mango-" + UUID.randomUUID();

		var created = mvc.post()
			.uri("/api/v1/admin/products")
			.with(admin(UUID.randomUUID()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "name": "%s", "categoryId": "%s", "price": { "amount": "5.00", "currency": "PEN" },
					  "displayOrder": 0, "quickSalePinned": false }
					""".formatted(name, categoryId))
			.exchange();
		assertThat(created).hasStatus(HttpStatus.CREATED);

		var audit = mvc.get()
			.uri(AUDIT_ENTRIES + "?entityType=PRODUCT&entityId=" + idOf(created))
			.with(admin(UUID.randomUUID()))
			.exchange();

		assertThat(audit).hasStatusOk();
		assertThat(audit).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
		assertThat(audit).bodyJson().extractingPath("$.content[0].action").isEqualTo("PRODUCT_CREATED");
		assertThat(audit).bodyJson().extractingPath("$.content[0].after.name").isEqualTo(name);
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static RequestPostProcessor admin(UUID id) {
		return AuthenticatedAs.user(id, Role.ADMIN);
	}

}
