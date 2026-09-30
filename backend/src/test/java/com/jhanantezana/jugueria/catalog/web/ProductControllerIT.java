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
import com.jhanantezana.jugueria.catalog.ProductChanged;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.CatalogTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ProductControllerIT {

	static final String PRODUCTS = "/api/v1/admin/products";

	static final String CATEGORIES = "/api/v1/admin/categories";

	static final String GROUPS = "/api/v1/admin/modifier-groups";

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
	void createsAProductWithAllergensAndModifierGroups() {
		var categoryId = createCategory();
		var sizeId = createGroup("Size-" + UUID.randomUUID());
		var boostersId = createGroup("Boosters-" + UUID.randomUUID());

		var result = create(product("Mango-" + UUID.randomUUID(), categoryId, "12.50", "[\"SOY\",\"MILK\"]",
				"[\"" + boostersId + "\",\"" + sizeId + "\"]"));

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.LOCATION,
				values -> assertThat(values).singleElement().asString().startsWith(PRODUCTS + "/"));
		assertThat(result).bodyJson().extractingPath("$.categoryId").isEqualTo(categoryId);
		assertThat(result).bodyJson().extractingPath("$.price.amount").isEqualTo("12.50");
		assertThat(result).bodyJson().extractingPath("$.price.currency").isEqualTo("PEN");
		assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.imageUrl").isNull();
		assertThat(result).bodyJson().extractingPath("$.allergens").asList().containsExactly("MILK", "SOY");
		assertThat(result).bodyJson().extractingPath("$.modifierGroupIds").asList().containsExactly(boostersId, sizeId);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"0\"");
		assertThat(events.stream(ProductChanged.class)).singleElement().satisfies(event -> {
			assertThat(event.before()).isNull();
			assertThat(event.after().price().amount()).hasToString("12.50");
		});
	}

	@Test
	void rejectsAnUnknownCategory() {
		var result = create(product("Mango-" + UUID.randomUUID(), UUID.randomUUID().toString(), "12.50", "[]", "[]"));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.unknown-category");
	}

	@Test
	void rejectsAnUnknownModifierGroup() {
		var categoryId = createCategory();

		var result = create(product("Mango-" + UUID.randomUUID(), categoryId, "12.50", "[]",
				"[\"" + UUID.randomUUID() + "\"]"));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.unknown-modifier-group");
	}

	@Test
	void rejectsTheSameModifierGroupTwice() {
		var categoryId = createCategory();
		var groupId = createGroup("Size-" + UUID.randomUUID());

		var result = create(product("Mango-" + UUID.randomUUID(), categoryId, "12.50", "[]",
				"[\"" + groupId + "\",\"" + groupId + "\"]"));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.invalid-product");
	}

	@Test
	void rejectsAZeroPrice() {
		var result = create(product("Mango-" + UUID.randomUUID(), createCategory(), "0.00", "[]", "[]"));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.invalid-price");
	}

	@Test
	void rejectsAPriceBeyondWhatTheColumnHoldsInsteadOfFailingWith500() {
		var result = create(product("Mango-" + UUID.randomUUID(), createCategory(), "10000000000.00", "[]", "[]"));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.invalid-price");
	}

	@Test
	void rejectsAPriceInAnotherCurrency() {
		var result = create("""
				{ "name": "Mango-%s", "categoryId": "%s", "price": { "amount": "5.00", "currency": "USD" },
				  "displayOrder": 0, "quickSalePinned": false }
				""".formatted(UUID.randomUUID(), createCategory()));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.currency-mismatch");
	}

	@Test
	void rejectsAnUnknownAllergen() {
		var result = create(product("Mango-" + UUID.randomUUID(), createCategory(), "5.00", "[\"DUST\"]", "[]"));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void rejectsADuplicateNameInTheSameCategoryIgnoringCase() {
		var categoryId = createCategory();
		var name = "Mango-" + UUID.randomUUID();
		create(product(name, categoryId, "5.00", "[]", "[]"));

		var result = create(product(name.toUpperCase(), categoryId, "6.00", "[]", "[]"));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.product-name-already-used");
	}

	@Test
	void allowsTheSameNameInAnotherCategory() {
		var name = "Mango-" + UUID.randomUUID();
		create(product(name, createCategory(), "5.00", "[]", "[]"));

		var result = create(product(name, createCategory(), "5.00", "[]", "[]"));

		assertThat(result).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void returnsAProductWithItsETag() {
		var created = create(product("Mango-" + UUID.randomUUID(), createCategory(), "5.00", "[\"MILK\"]", "[]"));

		var result = mvc.get().uri(PRODUCTS + "/" + idOf(created)).with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG,
				values -> assertThat(values).containsExactly("\"0\""));
		assertThat(result).bodyJson().extractingPath("$.allergens").asList().containsExactly("MILK");
	}

	@Test
	void reportsAnUnknownProduct() {
		var result = mvc.get().uri(PRODUCTS + "/" + UUID.randomUUID()).with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.product-not-found");
	}

	@Test
	void listsProductsPagedAndFilteredByCategory() {
		var juices = createCategory();
		var sandwiches = createCategory();
		var mango = create(product("Mango-" + UUID.randomUUID(), juices, "5.00", "[]", "[]"));
		create(product("Ham-" + UUID.randomUUID(), sandwiches, "9.00", "[]", "[]"));

		var result = mvc.get().uri(PRODUCTS + "?categoryId=" + juices + "&page=0&size=10").with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.content[0].id").isEqualTo(idOf(mango));
		assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(10);
	}

	@Test
	void capsThePageSize() {
		var result = mvc.get().uri(PRODUCTS + "?size=1000").with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(100);
	}

	@Test
	void updatesAProductAndRecordsThePriceChange() {
		var categoryId = createCategory();
		var created = create(product("Mango-" + UUID.randomUUID(), categoryId, "12.50", "[]", "[]"));
		var groupId = createGroup("Size-" + UUID.randomUUID());
		var newName = "Mango XL-" + UUID.randomUUID();

		var result = update(idOf(created), etagOf(created),
				product(newName, categoryId, "14.00", "[\"SOY\"]", "[\"" + groupId + "\"]"));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo(newName);
		assertThat(result).bodyJson().extractingPath("$.price.amount").isEqualTo("14.00");
		assertThat(result).bodyJson().extractingPath("$.allergens").asList().containsExactly("SOY");
		assertThat(result).bodyJson().extractingPath("$.modifierGroupIds").asList().containsExactly(groupId);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"1\"");
		var change = events.stream(ProductChanged.class).toList().get(1);
		assertThat(change.before().price().amount()).hasToString("12.50");
		assertThat(change.after().price().amount()).hasToString("14.00");
	}

	@Test
	void updatingTheGroupsAgainMovesTheETagOnce() {
		var categoryId = createCategory();
		var groupA = createGroup("A-" + UUID.randomUUID());
		var groupB = createGroup("B-" + UUID.randomUUID());
		var name = "Mango-" + UUID.randomUUID();
		var created = create(product(name, categoryId, "12.50", "[]", "[\"" + groupA + "\"]"));

		var result = update(idOf(created), etagOf(created),
				product(name, categoryId, "12.50", "[]", "[\"" + groupB + "\",\"" + groupA + "\"]"));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.modifierGroupIds").asList().containsExactly(groupB, groupA);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"1\"");
	}

	@Test
	void rejectsAnUpdateWithoutIfMatch() {
		var categoryId = createCategory();
		var created = create(product("Mango-" + UUID.randomUUID(), categoryId, "12.50", "[]", "[]"));

		var result = mvc.put()
			.uri(PRODUCTS + "/" + idOf(created))
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content(product("Other-" + UUID.randomUUID(), categoryId, "12.50", "[]", "[]"))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void aStaleUpdateIsRejectedAndTheOtherAdminsChangeIsKept() {
		var categoryId = createCategory();
		var created = create(product("Mango-" + UUID.randomUUID(), categoryId, "12.50", "[]", "[]"));
		var id = idOf(created);
		var staleEtag = etagOf(created);
		update(id, staleEtag, product("Winner-" + UUID.randomUUID(), categoryId, "20.00", "[]", "[]"));

		var late = update(id, staleEtag, product("Late-" + UUID.randomUUID(), categoryId, "1.00", "[]", "[]"));

		assertThat(late).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(late).bodyJson().extractingPath("$.currentETag").isEqualTo("\"1\"");
		var current = mvc.get().uri(PRODUCTS + "/" + id).with(admin()).exchange();
		assertThat(current).bodyJson().extractingPath("$.price.amount").isEqualTo("20.00");
	}

	@Test
	void reportsAnUnknownProductOnUpdate() {
		var result = update(UUID.randomUUID().toString(), "\"0\"",
				product("Ghost-" + UUID.randomUUID(), createCategory(), "5.00", "[]", "[]"));

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void deactivatesAndReactivatesAProduct() {
		var created = create(product("Mango-" + UUID.randomUUID(), createCategory(), "5.00", "[]", "[]"));
		var id = idOf(created);

		var deactivated = mvc.post()
			.uri(PRODUCTS + "/" + id + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();
		assertThat(deactivated).hasStatusOk();
		assertThat(deactivated).bodyJson().extractingPath("$.active").isEqualTo(false);

		var reactivated = mvc.post()
			.uri(PRODUCTS + "/" + id + "/reactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(deactivated))
			.exchange();
		assertThat(reactivated).hasStatusOk();
		assertThat(reactivated).bodyJson().extractingPath("$.active").isEqualTo(true);
		assertThat(events.stream(ProductChanged.class)).hasSize(3);
	}

	@Test
	void rejectsADeactivateWithoutIfMatch() {
		var created = create(product("Mango-" + UUID.randomUUID(), createCategory(), "5.00", "[]", "[]"));

		var result = mvc.post().uri(PRODUCTS + "/" + idOf(created) + "/deactivate").with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void rejectsEveryEndpointWhenAnonymous() {
		var id = UUID.randomUUID();
		var body = product("Mango", UUID.randomUUID().toString(), "5.00", "[]", "[]");

		assertThat(mvc.get().uri(PRODUCTS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri(PRODUCTS + "/" + id).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content(body).exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.put()
			.uri(PRODUCTS + "/" + id)
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(PRODUCTS + "/" + id + "/deactivate").header(HttpHeaders.IF_MATCH, "\"0\"").exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(PRODUCTS + "/" + id + "/reactivate").header(HttpHeaders.IF_MATCH, "\"0\"").exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsEveryEndpointForNonAdminRoles() {
		var id = UUID.randomUUID();
		var body = product("Mango", UUID.randomUUID().toString(), "5.00", "[]", "[]");
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.CUSTOMER }) {
			var user = AuthenticatedAs.user(UUID.randomUUID(), role);
			assertThat(mvc.get().uri(PRODUCTS).with(user).exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.get().uri(PRODUCTS + "/" + id).with(user).exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post().uri(PRODUCTS).with(user).contentType(MediaType.APPLICATION_JSON).content(body).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.put()
				.uri(PRODUCTS + "/" + id)
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post()
				.uri(PRODUCTS + "/" + id + "/deactivate")
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post()
				.uri(PRODUCTS + "/" + id + "/reactivate")
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
		}
	}

	private static String product(String name, String categoryId, String price, String allergens,
			String modifierGroupIds) {
		return """
				{ "name": "%s", "description": "Fresh", "categoryId": "%s",
				  "price": { "amount": "%s", "currency": "PEN" }, "displayOrder": 0, "quickSalePinned": false,
				  "allergens": %s, "modifierGroupIds": %s }
				""".formatted(name, categoryId, price, allergens, modifierGroupIds);
	}

	private String createCategory() {
		var result = mvc.post()
			.uri(CATEGORIES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Cat-%s\",\"displayOrder\":0}".formatted(UUID.randomUUID()))
			.exchange();
		return idOf(result);
	}

	private String createGroup(String name) {
		var result = mvc.post().uri(GROUPS).with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "%s", "required": false, "minChoices": 0, "maxChoices": 1,
				  "options": [ { "name": "A", "priceDelta": { "amount": "0.00", "currency": "PEN" } } ] }
				""".formatted(name)).exchange();
		return idOf(result);
	}

	private MvcTestResult create(String body) {
		return mvc.post().uri(PRODUCTS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private MvcTestResult update(String id, String etag, String body) {
		return mvc.put()
			.uri(PRODUCTS + "/" + id)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange();
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
