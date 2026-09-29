package com.jhanantezana.jugueria.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.CatalogTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MenuControllerIT {

	static final String MENU = "/api/v1/catalog/menu";

	static final String CATEGORIES = "/api/v1/admin/categories";

	static final String PRODUCTS = "/api/v1/admin/products";

	static final String GROUPS = "/api/v1/admin/modifier-groups";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		CatalogTables.clean(jdbc);
	}

	@Test
	void anAnonymousVisitorReadsTheMenuWithRevalidationHeaders() {
		var result = mvc.get().uri(MENU).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.CACHE_CONTROL,
				values -> assertThat(values).containsExactly("no-cache"));
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
	}

	@Test
	void groupsActiveProductsByCategoryInDisplayOrderWithEverythingACustomerNeeds() {
		var categoryB = createCategory("B-" + UUID.randomUUID(), 2);
		var categoryA = createCategory("A-" + UUID.randomUUID(), 1);
		var group = createGroup("Size-" + UUID.randomUUID());
		var mango = createProduct(categoryA, "Mango-" + UUID.randomUUID(), 1, "[\"MILK\"]", "[\"" + group + "\"]");
		createProduct(categoryA, "Apple-" + UUID.randomUUID(), 2, "[]", "[]");
		createProduct(categoryB, "Ham-" + UUID.randomUUID(), 1, "[]", "[]");

		var result = mvc.get().uri(MENU).exchange();

		assertThat(result).bodyJson().extractingPath("$.categories[*].id").asList().containsSequence(categoryA, categoryB);
		assertThat(result).bodyJson()
			.extractingPath("$.categories[?(@.id=='" + categoryA + "')].products[0].id")
			.asList()
			.containsExactly(mango);
		var product = "$.categories[?(@.id=='" + categoryA + "')].products[0]";
		assertThat(result).bodyJson().extractingPath(product + ".price.amount").asList().containsExactly("5.00");
		assertThat(result).bodyJson().extractingPath(product + ".available").asList().containsExactly(true);
		assertThat(result).bodyJson().extractingPath(product + ".imageUrl").asList().containsNull();
		assertThat(result).bodyJson().extractingPath(product + ".allergens[0]").asList().containsExactly("MILK");
		assertThat(result).bodyJson()
			.extractingPath(product + ".modifierGroups[0].id")
			.asList()
			.containsExactly(group);
		assertThat(result).bodyJson()
			.extractingPath(product + ".modifierGroups[0].options[*].name")
			.asList()
			.containsExactly("Small", "Large");
		assertThat(result).bodyJson()
			.extractingPath(product + ".modifierGroups[0].minChoices")
			.asList()
			.containsExactly(1);
	}

	@Test
	void hidesInactiveProductsAndProductsOfInactiveCategories() {
		var visible = createCategory("Visible-" + UUID.randomUUID(), 0);
		var hidden = createCategory("Hidden-" + UUID.randomUUID(), 1);
		var shown = createProduct(visible, "Shown-" + UUID.randomUUID(), 0, "[]", "[]");
		var deactivated = createProduct(visible, "Off-" + UUID.randomUUID(), 1, "[]", "[]");
		createProduct(hidden, "InHidden-" + UUID.randomUUID(), 0, "[]", "[]");
		post(PRODUCTS + "/" + deactivated + "/deactivate", etagOfProduct(deactivated));
		post(CATEGORIES + "/" + hidden + "/deactivate", etagOfCategory(hidden));

		var result = mvc.get().uri(MENU).exchange();

		assertThat(result).bodyJson().extractingPath("$.categories[*].id").asList().contains(visible).doesNotContain(hidden);
		assertThat(result).bodyJson()
			.extractingPath("$.categories[?(@.id=='" + visible + "')].products[*].id")
			.asList()
			.containsExactly(shown);
	}

	@Test
	void showsAnUnavailableProductAsUnavailableInsteadOfHidingIt() {
		var category = createCategory("Cat-" + UUID.randomUUID(), 0);
		var product = createProduct(category, "Mango-" + UUID.randomUUID(), 0, "[]", "[]");
		mvc.put()
			.uri("/api/v1/catalog/products/" + product + "/availability")
			.with(user(Role.SERVER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":false}")
			.exchange();

		var result = mvc.get().uri(MENU).exchange();

		assertThat(result).bodyJson()
			.extractingPath("$.categories[?(@.id=='" + category + "')].products[0].available")
			.asList()
			.containsExactly(false);
	}

	@Test
	void answers304ToAMatchingIfNoneMatch() {
		var first = mvc.get().uri(MENU).exchange();
		var etag = first.getResponse().getHeader(HttpHeaders.ETAG);

		var second = mvc.get().uri(MENU).header(HttpHeaders.IF_NONE_MATCH, etag).exchange();

		assertThat(second).hasStatus(HttpStatus.NOT_MODIFIED);
		assertThat(second).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).containsExactly(etag));
	}

	@Test
	void answersTheFullMenuToAStaleIfNoneMatch() {
		var result = mvc.get().uri(MENU).header(HttpHeaders.IF_NONE_MATCH, "\"stale\"").exchange();

		assertThat(result).hasStatusOk();
	}

	@Test
	void theETagIsStableWhileNothingChanges() {
		createCategory("Cat-" + UUID.randomUUID(), 0);

		assertThat(menuETag()).isEqualTo(menuETag());
	}

	// Each command below is one kind of catalog change; every one must show in the very next read, which fails
	// if any of them forgets to evict the cached menu.
	@Test
	void everyKindOfCatalogChangeIsVisibleInTheNextRead() {
		var group = createGroup("Boosters-" + UUID.randomUUID());
		var category = createCategory("Cat-" + UUID.randomUUID(), 0);
		var product = createProduct(category, "Mango-" + UUID.randomUUID(), 0, "[]", "[\"" + group + "\"]");
		var optionId = assertThat(mvc.get().uri(GROUPS + "/" + group).with(user(Role.ADMIN)).exchange()).bodyJson()
			.extractingPath("$.options[0].id")
			.actual()
			.toString();

		var seen = menuETag();
		seen = assertChanged(seen, "creating a category", () -> createCategory("New-" + UUID.randomUUID(), 3));
		seen = assertChanged(seen, "renaming a category", () -> update(CATEGORIES + "/" + category, etagOfCategory(category),
				"{\"name\":\"Renamed-%s\",\"stationId\":\"%s\",\"displayOrder\":0}".formatted(UUID.randomUUID(),
						defaultStation())));
		seen = assertChanged(seen, "creating a product", () -> createProduct(category, "Pear-" + UUID.randomUUID(), 5, "[]", "[]"));
		seen = assertChanged(seen, "changing a price", () -> update(PRODUCTS + "/" + product, etagOfProduct(product), """
				{ "name": "Mango repriced", "categoryId": "%s", "price": { "amount": "9.99", "currency": "PEN" },
				  "displayOrder": 0, "quickSalePinned": false, "modifierGroupIds": ["%s"] }
				""".formatted(category, group)));
		seen = assertChanged(seen, "marking a product unavailable", () -> mvc.put()
			.uri("/api/v1/catalog/products/" + product + "/availability")
			.with(user(Role.CASHIER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":false}")
			.exchange());
		seen = assertChanged(seen, "marking an option unavailable", () -> mvc.put()
			.uri("/api/v1/catalog/modifier-options/" + optionId + "/availability")
			.with(user(Role.SERVER))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":false}")
			.exchange());
		seen = assertChanged(seen, "changing an option's price", () -> update(GROUPS + "/" + group,
				etagOfGroup(group), """
						{ "name": "Boosters renamed", "required": false, "minChoices": 0, "maxChoices": 1,
						  "options": [ { "id": "%s", "name": "A", "priceDelta": { "amount": "4.00", "currency": "PEN" } } ] }
						""".formatted(optionId)));
		seen = assertChanged(seen, "deactivating a product",
				() -> post(PRODUCTS + "/" + product + "/deactivate", etagOfProduct(product)));
		seen = assertChanged(seen, "reactivating a product",
				() -> post(PRODUCTS + "/" + product + "/reactivate", etagOfProduct(product)));
		assertChanged(seen, "deactivating a category",
				() -> post(CATEGORIES + "/" + category + "/deactivate", etagOfCategory(category)));
	}

	private String assertChanged(String before, String change, Runnable action) {
		action.run();
		var after = menuETag();
		assertThat(after).as("menu ETag after " + change).isNotEqualTo(before);
		return after;
	}

	private String menuETag() {
		return mvc.get().uri(MENU).exchange().getResponse().getHeader(HttpHeaders.ETAG);
	}

	private String defaultStation() {
		return jdbc.sql("select id from catalog.station where default_station").query(UUID.class).single().toString();
	}

	private String createCategory(String name, int displayOrder) {
		var result = mvc.post()
			.uri(CATEGORIES)
			.with(user(Role.ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\",\"displayOrder\":%d}".formatted(name, displayOrder))
			.exchange();
		return idOf(result);
	}

	private String createGroup(String name) {
		var result = mvc.post().uri(GROUPS).with(user(Role.ADMIN)).contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "%s", "required": true, "minChoices": 1, "maxChoices": 1,
				  "options": [ { "name": "Small", "priceDelta": { "amount": "0.00", "currency": "PEN" } },
				               { "name": "Large", "priceDelta": { "amount": "2.00", "currency": "PEN" } } ] }
				""".formatted(name)).exchange();
		return idOf(result);
	}

	private String createProduct(String categoryId, String name, int displayOrder, String allergens, String groups) {
		var result = mvc.post().uri(PRODUCTS).with(user(Role.ADMIN)).contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "%s", "categoryId": "%s", "price": { "amount": "5.00", "currency": "PEN" },
				  "displayOrder": %d, "quickSalePinned": false, "allergens": %s, "modifierGroupIds": %s }
				""".formatted(name, categoryId, displayOrder, allergens, groups)).exchange();
		return idOf(result);
	}

	private MvcTestResult update(String uri, String etag, String body) {
		return mvc.put()
			.uri(uri)
			.with(user(Role.ADMIN))
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
	}

	private MvcTestResult post(String uri, String etag) {
		return mvc.post().uri(uri).with(user(Role.ADMIN)).header(HttpHeaders.IF_MATCH, etag).exchange();
	}

	private String etagOfProduct(String id) {
		return etagOf(PRODUCTS + "/" + id);
	}

	private String etagOfGroup(String id) {
		return etagOf(GROUPS + "/" + id);
	}

	private String etagOfCategory(String id) {
		var categories = mvc.get().uri(CATEGORIES).with(user(Role.ADMIN)).exchange();
		var etags = (List<?>) assertThat(categories).bodyJson()
			.extractingPath("$[?(@.id=='" + id + "')].etag")
			.actual();
		return etags.getFirst().toString();
	}

	private String etagOf(String uri) {
		return mvc.get().uri(uri).with(user(Role.ADMIN)).exchange().getResponse().getHeader(HttpHeaders.ETAG);
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static RequestPostProcessor user(Role role) {
		return AuthenticatedAs.user(UUID.randomUUID(), role);
	}

}
