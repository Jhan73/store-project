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
import com.jhanantezana.jugueria.catalog.ModifierGroupChanged;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.CatalogTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ModifierGroupControllerIT {

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
	void createsAGroupWithItsOptions() {
		var result = create(sizeGroup("Size-" + UUID.randomUUID()));

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(result).bodyJson().extractingPath("$.required").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.minChoices").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.maxChoices").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.options[*].name").asList().containsExactly("Small", "Large");
		assertThat(result).bodyJson().extractingPath("$.options[1].priceDelta.amount").isEqualTo("2.50");
		assertThat(result).bodyJson().extractingPath("$.options[1].priceDelta.currency").isEqualTo("PEN");
		assertThat(result).bodyJson().extractingPath("$.options[0].available").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"0\"");
		assertThat(events.stream(ModifierGroupChanged.class)).singleElement().satisfies(event -> {
			assertThat(event.before()).isNull();
			assertThat(event.after().options()).hasSize(2);
		});
	}

	@Test
	void keepsTheAllergensOfAnOption() {
		var body = """
				{ "name": "Boosters-%s", "required": false, "minChoices": 0, "maxChoices": 2,
				  "options": [
				    { "name": "Peanut butter", "priceDelta": { "amount": "1.00", "currency": "PEN" },
				      "allergens": ["PEANUTS", "SOY"] },
				    { "name": "Chia", "priceDelta": { "amount": "0.00", "currency": "PEN" } } ] }
				""".formatted(UUID.randomUUID());

		var result = create(body);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.options[0].allergens").asList().containsExactly("PEANUTS", "SOY");
		assertThat(result).bodyJson().extractingPath("$.options[1].allergens").asList().isEmpty();
	}

	@Test
	void rejectsAMinimumAboveTheMaximum() {
		var result = create("""
				{ "name": "Bad-%s", "required": true, "minChoices": 2, "maxChoices": 1,
				  "options": [ { "name": "A", "priceDelta": { "amount": "0.00", "currency": "PEN" } },
				               { "name": "B", "priceDelta": { "amount": "0.00", "currency": "PEN" } } ] }
				""".formatted(UUID.randomUUID()));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.invalid-modifier-group");
	}

	@Test
	void rejectsARequiredGroupThatAllowsNoChoice() {
		var result = create("""
				{ "name": "Bad-%s", "required": true, "minChoices": 0, "maxChoices": 1,
				  "options": [ { "name": "A", "priceDelta": { "amount": "0.00", "currency": "PEN" } } ] }
				""".formatted(UUID.randomUUID()));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.invalid-modifier-group");
	}

	@Test
	void rejectsAnOptionPricedInAnotherCurrency() {
		var result = create("""
				{ "name": "Bad-%s", "required": false, "minChoices": 0, "maxChoices": 1,
				  "options": [ { "name": "A", "priceDelta": { "amount": "1.00", "currency": "USD" } } ] }
				""".formatted(UUID.randomUUID()));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.currency-mismatch");
	}

	@Test
	void rejectsANegativeOptionPrice() {
		var result = create("""
				{ "name": "Bad-%s", "required": false, "minChoices": 0, "maxChoices": 1,
				  "options": [ { "name": "A", "priceDelta": { "amount": "-1.00", "currency": "PEN" } } ] }
				""".formatted(UUID.randomUUID()));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.invalid-price");
	}

	@Test
	void rejectsAnOptionPriceBeyondWhatTheColumnHolds() {
		var result = create("""
				{ "name": "Bad-%s", "required": false, "minChoices": 0, "maxChoices": 1,
				  "options": [ { "name": "A", "priceDelta": { "amount": "10000000000.00", "currency": "PEN" } } ] }
				""".formatted(UUID.randomUUID()));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.invalid-price");
	}

	@Test
	void rejectsAGroupWithoutOptions() {
		var result = create("""
				{ "name": "Bad-%s", "required": false, "minChoices": 0, "maxChoices": 1, "options": [] }
				""".formatted(UUID.randomUUID()));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.validation-failed");
	}

	@Test
	void rejectsADuplicateGroupNameIgnoringCase() {
		var name = "Size-" + UUID.randomUUID();
		create(sizeGroup(name));

		var result = create(sizeGroup(name.toUpperCase()));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.modifier-group-name-already-used");
	}

	@Test
	void returnsAGroupWithItsETag() {
		var created = create(sizeGroup("Size-" + UUID.randomUUID()));

		var result = mvc.get().uri(GROUPS + "/" + idOf(created)).with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG,
				values -> assertThat(values).containsExactly("\"0\""));
		assertThat(result).bodyJson().extractingPath("$.options[*].name").asList().containsExactly("Small", "Large");
	}

	@Test
	void listsGroupsWithTheirOptions() {
		var name = "Size-" + UUID.randomUUID();
		var created = create(sizeGroup(name));

		var result = mvc.get().uri(GROUPS).with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson()
			.extractingPath("$[?(@.id=='" + idOf(created) + "')].options[*].name")
			.asList()
			.containsExactly("Small", "Large");
	}

	@Test
	void reportsAnUnknownGroup() {
		var result = mvc.get().uri(GROUPS + "/" + UUID.randomUUID()).with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.modifier-group-not-found");
	}

	@Test
	void updatesAGroupKeepingTheOptionsItNamesAndDroppingTheOthers() {
		var created = create(sizeGroup("Size-" + UUID.randomUUID()));
		var smallId = assertThat(created).bodyJson().extractingPath("$.options[0].id").actual().toString();

		var result = update(idOf(created), etagOf(created), """
				{ "name": "Sizes-%s", "required": true, "minChoices": 1, "maxChoices": 1,
				  "options": [
				    { "id": "%s", "name": "Small", "priceDelta": { "amount": "0.50", "currency": "PEN" } },
				    { "name": "Medium", "priceDelta": { "amount": "1.50", "currency": "PEN" } } ] }
				""".formatted(UUID.randomUUID(), smallId));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.options[*].name").asList().containsExactly("Small", "Medium");
		assertThat(result).bodyJson().extractingPath("$.options[0].id").isEqualTo(smallId);
		assertThat(result).bodyJson().extractingPath("$.options[0].priceDelta.amount").isEqualTo("0.50");
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"1\"");
		assertThat(events.stream(ModifierGroupChanged.class)).hasSize(2);
		assertThat(events.stream(ModifierGroupChanged.class).toList().get(1)).satisfies(event -> {
			assertThat(event.before().options()).hasSize(2);
			assertThat(event.after().options()).hasSize(2);
		});
	}

	@Test
	void changingOnlyAnOptionStillMovesTheGroupsETag() {
		var name = "Size-" + UUID.randomUUID();
		var created = create(sizeGroup(name));
		var smallId = assertThat(created).bodyJson().extractingPath("$.options[0].id").actual().toString();
		var largeId = assertThat(created).bodyJson().extractingPath("$.options[1].id").actual().toString();

		var result = update(idOf(created), etagOf(created), """
				{ "name": "%s", "required": true, "minChoices": 1, "maxChoices": 1,
				  "options": [
				    { "id": "%s", "name": "Small", "priceDelta": { "amount": "0.00", "currency": "PEN" } },
				    { "id": "%s", "name": "Large", "priceDelta": { "amount": "3.00", "currency": "PEN" } } ] }
				""".formatted(name, smallId, largeId));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.options[1].priceDelta.amount").isEqualTo("3.00");
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"1\"");
		assertThat(mvc.get().uri(GROUPS + "/" + idOf(created)).with(admin()).exchange()).headers()
			.hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).containsExactly("\"1\""));
	}

	@Test
	void rejectsAnOptionFromAnotherGroup() {
		var created = create(sizeGroup("Size-" + UUID.randomUUID()));

		var result = update(idOf(created), etagOf(created), """
				{ "name": "Size-%s", "required": false, "minChoices": 0, "maxChoices": 1,
				  "options": [ { "id": "%s", "name": "Stranger",
				                 "priceDelta": { "amount": "0.00", "currency": "PEN" } } ] }
				""".formatted(UUID.randomUUID(), UUID.randomUUID()));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.invalid-modifier-group");
	}

	@Test
	void rejectsAnUpdateWithoutIfMatch() {
		var created = create(sizeGroup("Size-" + UUID.randomUUID()));

		var result = mvc.put()
			.uri(GROUPS + "/" + idOf(created))
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content(sizeGroup("Other-" + UUID.randomUUID()))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void aStaleUpdateIsRejectedAndTheOtherAdminsChangeIsKept() {
		var created = create(sizeGroup("Size-" + UUID.randomUUID()));
		var id = idOf(created);
		var staleEtag = etagOf(created);
		var winner = "Winner-" + UUID.randomUUID();
		update(id, staleEtag, sizeGroup(winner));

		var late = update(id, staleEtag, sizeGroup("Late-" + UUID.randomUUID()));

		assertThat(late).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(late).bodyJson().extractingPath("$.currentETag").isEqualTo("\"1\"");
		var current = mvc.get().uri(GROUPS + "/" + id).with(admin()).exchange();
		assertThat(current).bodyJson().extractingPath("$.name").isEqualTo(winner);
	}

	@Test
	void reportsAnUnknownGroupOnUpdate() {
		var result = update(UUID.randomUUID().toString(), "\"0\"", sizeGroup("Ghost-" + UUID.randomUUID()));

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void deletesAGroupAndPublishesTheChange() {
		var created = create(sizeGroup("Size-" + UUID.randomUUID()));

		var result = mvc.delete()
			.uri(GROUPS + "/" + idOf(created))
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.get().uri(GROUPS + "/" + idOf(created)).with(admin()).exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(events.stream(ModifierGroupChanged.class).toList().get(1)).satisfies(event -> {
			assertThat(event.before()).isNotNull();
			assertThat(event.after()).isNull();
		});
	}

	@Test
	void refusesToDeleteAGroupAProductStillUses() {
		var created = create(sizeGroup("Size-" + UUID.randomUUID()));
		var category = mvc.post()
			.uri("/api/v1/admin/categories")
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Cat-%s\",\"displayOrder\":0}".formatted(UUID.randomUUID()))
			.exchange();
		mvc.post().uri("/api/v1/admin/products").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "Mango-%s", "categoryId": "%s", "price": { "amount": "5.00", "currency": "PEN" },
				  "displayOrder": 0, "quickSalePinned": false, "modifierGroupIds": ["%s"] }
				""".formatted(UUID.randomUUID(), idOf(category), idOf(created))).exchange();

		var result = mvc.delete()
			.uri(GROUPS + "/" + idOf(created))
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.modifier-group-in-use");
		assertThat(mvc.get().uri(GROUPS + "/" + idOf(created)).with(admin()).exchange()).hasStatusOk();
	}

	@Test
	void rejectsADeleteWithoutIfMatch() {
		var created = create(sizeGroup("Size-" + UUID.randomUUID()));

		var result = mvc.delete().uri(GROUPS + "/" + idOf(created)).with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void aStaleDeleteIsRejected() {
		var name = "Size-" + UUID.randomUUID();
		var created = create(sizeGroup(name));
		update(idOf(created), etagOf(created), sizeGroup(name + "-b"));

		var result = mvc.delete()
			.uri(GROUPS + "/" + idOf(created))
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_FAILED);
	}

	@Test
	void reportsAnUnknownGroupOnDelete() {
		var result = mvc.delete()
			.uri(GROUPS + "/" + UUID.randomUUID())
			.with(admin())
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void rejectsEveryEndpointWhenAnonymous() {
		var id = UUID.randomUUID();
		var body = sizeGroup("Size-" + UUID.randomUUID());

		assertThat(mvc.get().uri(GROUPS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri(GROUPS + "/" + id).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(GROUPS).contentType(MediaType.APPLICATION_JSON).content(body).exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.put()
			.uri(GROUPS + "/" + id)
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.delete().uri(GROUPS + "/" + id).header(HttpHeaders.IF_MATCH, "\"0\"").exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsEveryEndpointForNonAdminRoles() {
		var id = UUID.randomUUID();
		var body = sizeGroup("Size-" + UUID.randomUUID());
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.CUSTOMER }) {
			var user = AuthenticatedAs.user(UUID.randomUUID(), role);
			assertThat(mvc.get().uri(GROUPS).with(user).exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.get().uri(GROUPS + "/" + id).with(user).exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post().uri(GROUPS).with(user).contentType(MediaType.APPLICATION_JSON).content(body).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.put()
				.uri(GROUPS + "/" + id)
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.delete().uri(GROUPS + "/" + id).with(user).header(HttpHeaders.IF_MATCH, "\"0\"").exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		}
	}

	private static String sizeGroup(String name) {
		return """
				{ "name": "%s", "required": true, "minChoices": 1, "maxChoices": 1,
				  "options": [
				    { "name": "Small", "priceDelta": { "amount": "0.00", "currency": "PEN" } },
				    { "name": "Large", "priceDelta": { "amount": "2.50", "currency": "PEN" } } ] }
				""".formatted(name);
	}

	private MvcTestResult create(String body) {
		return mvc.post().uri(GROUPS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
	}

	private MvcTestResult update(String id, String etag, String body) {
		return mvc.put()
			.uri(GROUPS + "/" + id)
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
