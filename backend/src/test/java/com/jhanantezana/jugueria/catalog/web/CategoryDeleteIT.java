package com.jhanantezana.jugueria.catalog.web;

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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.catalog.CatalogChangeKind;
import com.jhanantezana.jugueria.catalog.CategoryChanged;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.CatalogTables;
import com.jhanantezana.testsupport.InventoryTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class CategoryDeleteIT {

	static final String CATEGORIES = "/api/v1/admin/categories";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@AfterEach
	void cleanUp() {
		CatalogTables.clean(jdbc);
	}

	@Test
	void deletesAnEmptyCategoryAndPublishesItsSnapshot() {
		var category = createCategory();

		var result = delete(idOf(category), etagOf(category));

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(count(idOf(category))).isZero();
		assertThat(events.stream(CategoryChanged.class).filter(event -> event.kind() == CatalogChangeKind.DELETED))
			.singleElement()
			.satisfies(event -> {
				assertThat(event.before()).isNotNull();
				assertThat(event.after()).isNull();
				assertThat(event.categoryId()).isEqualTo(UUID.fromString(idOf(category)));
			});
	}

	@Test
	void refusesToDeleteACategoryThatHasProducts() {
		var productId = InventoryTables.aProduct(jdbc);
		var categoryId = jdbc.sql("select category_id from catalog.product where id = ?")
			.param(productId)
			.query(UUID.class)
			.single();

		var result = delete(categoryId.toString(), "\"0\"");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.category-in-use");
		assertThat(count(categoryId.toString())).isEqualTo(1);
	}

	@Test
	void aCategoryWithOnlyInactiveProductsStillCannotBeDeleted() {
		var productId = InventoryTables.aProduct(jdbc);
		jdbc.sql("update catalog.product set active = false where id = ?").param(productId).update();
		var categoryId = jdbc.sql("select category_id from catalog.product where id = ?")
			.param(productId)
			.query(UUID.class)
			.single();

		assertThat(delete(categoryId.toString(), "\"0\"")).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void theListSaysWhichCategoriesCanBeDeleted() {
		var empty = createCategory();
		var productId = InventoryTables.aProduct(jdbc);
		var usedId = jdbc.sql("select category_id from catalog.product where id = ?")
			.param(productId)
			.query(UUID.class)
			.single();

		var result = mvc.get().uri(CATEGORIES).with(admin()).exchange();

		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='%s')].deletable".formatted(idOf(empty)))
			.asList()
			.containsExactly(true);
		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='%s')].deletable".formatted(usedId))
			.asList()
			.containsExactly(false);
	}

	@Test
	void aSingleCategoryResponseSaysIfItCanBeDeleted() {
		var created = createCategory();

		assertThat(created).bodyJson().extractingPath("$.deletable").isEqualTo(true);
	}

	@Test
	void aStaleOrMissingIfMatchIsRejectedAndAnUnknownCategoryIsNotFound() {
		var category = createCategory();

		assertThat(mvc.delete().uri(CATEGORIES + "/" + idOf(category)).with(admin()).exchange())
			.hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(delete(idOf(category), "\"9\"")).hasStatus(HttpStatus.PRECONDITION_FAILED);
		var unknown = delete(UUID.randomUUID().toString(), "\"0\"");
		assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("catalog.category-not-found");
		assertThat(count(idOf(category))).isEqualTo(1);
	}

	@Test
	void onlyAnAdminMayDelete() {
		var category = createCategory();

		var result = mvc.delete()
			.uri(CATEGORIES + "/" + idOf(category))
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER))
			.header(HttpHeaders.IF_MATCH, etagOf(category))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	private MvcTestResult createCategory() {
		return mvc.post()
			.uri(CATEGORIES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Cat-%s\",\"displayOrder\":0}".formatted(UUID.randomUUID()))
			.exchange();
	}

	private MvcTestResult delete(String id, String etag) {
		return mvc.delete().uri(CATEGORIES + "/" + id).with(admin()).header(HttpHeaders.IF_MATCH, etag).exchange();
	}

	private long count(String id) {
		return jdbc.sql("select count(*) from catalog.category where id = ?")
			.param(UUID.fromString(id))
			.query(Long.class)
			.single();
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
