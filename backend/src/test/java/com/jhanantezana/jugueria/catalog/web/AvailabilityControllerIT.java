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
import com.jhanantezana.jugueria.catalog.AvailabilityChanged;
import com.jhanantezana.jugueria.catalog.AvailabilityTarget;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.CatalogTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class AvailabilityControllerIT {

	static final String PRODUCTS = "/api/v1/admin/products";

	static final String GROUPS = "/api/v1/admin/modifier-groups";

	static final String PRODUCT_AVAILABILITY = "/api/v1/catalog/products/%s/availability";

	static final String OPTION_AVAILABILITY = "/api/v1/catalog/modifier-options/%s/availability";

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
	void marksAProductUnavailableAndPublishesTheChange() {
		var product = createProduct("Mango-" + UUID.randomUUID());
		var actorId = UUID.randomUUID();

		var result = setProductAvailability(idOf(product), false, AuthenticatedAs.user(actorId, Role.SERVER));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(idOf(product));
		assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(false);
		assertThat(events.stream(AvailabilityChanged.class)).singleElement().satisfies(event -> {
			assertThat(event.target()).isEqualTo(AvailabilityTarget.PRODUCT);
			assertThat(event.targetId()).hasToString(idOf(product));
			assertThat(event.available()).isFalse();
			assertThat(event.actorId()).isEqualTo(actorId);
			assertThat(event.actorRole()).isEqualTo(Role.SERVER);
		});
	}

	@Test
	void marksAProductAvailableAgain() {
		var id = idOf(createProduct("Mango-" + UUID.randomUUID()));
		setProductAvailability(id, false, cashier());

		var result = setProductAvailability(id, true, cashier());

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(true);
		assertThat(events.stream(AvailabilityChanged.class).map(AvailabilityChanged::available))
			.containsExactly(false, true);
	}

	@Test
	void repeatingTheCurrentAvailabilityChangesNothingAndPublishesNothing() {
		var id = idOf(createProduct("Mango-" + UUID.randomUUID()));

		var result = setProductAvailability(id, true, admin());

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(true);
		assertThat(events.stream(AvailabilityChanged.class)).isEmpty();
	}

	@Test
	void anAvailabilityChangeDoesNotMoveTheProductsETag() {
		var product = createProduct("Mango-" + UUID.randomUUID());
		setProductAvailability(idOf(product), false, admin());

		var current = mvc.get().uri(PRODUCTS + "/" + idOf(product)).with(admin()).exchange();

		assertThat(current).headers().hasHeaderSatisfying(HttpHeaders.ETAG,
				values -> assertThat(values).containsExactly("\"0\""));
		assertThat(current).bodyJson().extractingPath("$.available").isEqualTo(false);
	}

	@Test
	void anAdminSaveWithAStaleViewNeverUndoesTheStaffs86() {
		var product = createProduct("Mango-" + UUID.randomUUID());
		var id = idOf(product);
		setProductAvailability(id, false, cashier());

		var result = mvc.put()
			.uri(PRODUCTS + "/" + id)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(product))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "name": "Mango renamed-%s", "categoryId": "%s", "price": { "amount": "9.00", "currency": "PEN" },
					  "displayOrder": 0, "quickSalePinned": false }
					""".formatted(UUID.randomUUID(), categoryIdOf(product)))
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(false);
	}

	@Test
	void reportsAnUnknownProduct() {
		var result = setProductAvailability(UUID.randomUUID().toString(), false, admin());

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.product-not-found");
	}

	@Test
	void anEmptyBodyIsAValidationErrorNotAnImplicitFalse() {
		var id = UUID.randomUUID();

		for (var uri : new String[] { PRODUCT_AVAILABILITY.formatted(id), OPTION_AVAILABILITY.formatted(id) }) {
			var result = mvc.put()
				.uri(uri)
				.with(admin())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}")
				.exchange();

			assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.validation-failed");
			assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("available");
		}
	}

	@Test
	void everyStaffRoleMayToggleAProduct() {
		var id = idOf(createProduct("Mango-" + UUID.randomUUID()));

		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.ADMIN }) {
			var result = setProductAvailability(id, false, AuthenticatedAs.user(UUID.randomUUID(), role));
			assertThat(result).hasStatusOk();
			setProductAvailability(id, true, admin());
		}
	}

	@Test
	void aCustomerMayNotToggleAProduct() {
		var id = idOf(createProduct("Mango-" + UUID.randomUUID()));

		var result = setProductAvailability(id, false, AuthenticatedAs.user(UUID.randomUUID(), Role.CUSTOMER));

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void anAnonymousCallerMayNotToggleAProduct() {
		var result = mvc.put()
			.uri(PRODUCT_AVAILABILITY.formatted(UUID.randomUUID()))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":false}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void marksAModifierOptionUnavailable() {
		var group = createGroup("Boosters-" + UUID.randomUUID());
		var optionId = assertThat(group).bodyJson().extractingPath("$.options[0].id").actual().toString();

		var result = setOptionAvailability(optionId, false, AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.available").isEqualTo(false);
		assertThat(events.stream(AvailabilityChanged.class)).singleElement().satisfies(event -> {
			assertThat(event.target()).isEqualTo(AvailabilityTarget.MODIFIER_OPTION);
			assertThat(event.targetId()).hasToString(optionId);
		});
		var current = mvc.get().uri(GROUPS + "/" + idOf(group)).with(admin()).exchange();
		assertThat(current).bodyJson().extractingPath("$.options[0].available").isEqualTo(false);
		assertThat(current).headers().hasHeaderSatisfying(HttpHeaders.ETAG,
				values -> assertThat(values).containsExactly("\"0\""));
	}

	@Test
	void anAdminSaveOfAGroupNeverUndoesAnOption86() {
		var group = createGroup("Boosters-" + UUID.randomUUID());
		var optionId = assertThat(group).bodyJson().extractingPath("$.options[0].id").actual().toString();
		setOptionAvailability(optionId, false, cashier());

		var result = mvc.put()
			.uri(GROUPS + "/" + idOf(group))
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(group))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "name": "Boosters renamed-%s", "required": false, "minChoices": 0, "maxChoices": 1,
					  "options": [ { "id": "%s", "name": "A", "priceDelta": { "amount": "1.00", "currency": "PEN" } } ] }
					""".formatted(UUID.randomUUID(), optionId))
			.exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.options[0].available").isEqualTo(false);
	}

	@Test
	void repeatingTheCurrentOptionAvailabilityPublishesNothing() {
		var group = createGroup("Boosters-" + UUID.randomUUID());
		var optionId = assertThat(group).bodyJson().extractingPath("$.options[0].id").actual().toString();

		var result = setOptionAvailability(optionId, true, cashier());

		assertThat(result).hasStatusOk();
		assertThat(events.stream(AvailabilityChanged.class)).isEmpty();
	}

	@Test
	void reportsAnUnknownOption() {
		var result = setOptionAvailability(UUID.randomUUID().toString(), false, admin());

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.modifier-option-not-found");
	}

	@Test
	void aCustomerMayNotToggleAnOptionAndAnAnonymousCallerNeitherCan() {
		var id = UUID.randomUUID().toString();

		assertThat(setOptionAvailability(id, false, AuthenticatedAs.user(UUID.randomUUID(), Role.CUSTOMER)))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.put()
			.uri(OPTION_AVAILABILITY.formatted(id))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":false}")
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult createProduct(String name) {
		var category = mvc.post()
			.uri("/api/v1/admin/categories")
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Cat-%s\",\"displayOrder\":0}".formatted(UUID.randomUUID()))
			.exchange();
		return mvc.post().uri(PRODUCTS).with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "%s", "categoryId": "%s", "price": { "amount": "5.00", "currency": "PEN" },
				  "displayOrder": 0, "quickSalePinned": false }
				""".formatted(name, idOf(category))).exchange();
	}

	private MvcTestResult createGroup(String name) {
		return mvc.post().uri(GROUPS).with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "%s", "required": false, "minChoices": 0, "maxChoices": 1,
				  "options": [ { "name": "A", "priceDelta": { "amount": "0.00", "currency": "PEN" } } ] }
				""".formatted(name)).exchange();
	}

	private MvcTestResult setProductAvailability(String id, boolean available, RequestPostProcessor user) {
		return mvc.put()
			.uri(PRODUCT_AVAILABILITY.formatted(id))
			.with(user)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":%s}".formatted(available))
			.exchange();
	}

	private MvcTestResult setOptionAvailability(String id, boolean available, RequestPostProcessor user) {
		return mvc.put()
			.uri(OPTION_AVAILABILITY.formatted(id))
			.with(user)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"available\":%s}".formatted(available))
			.exchange();
	}

	private static String idOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.id").actual().toString();
	}

	private static String categoryIdOf(MvcTestResult result) {
		return assertThat(result).bodyJson().extractingPath("$.categoryId").actual().toString();
	}

	private static String etagOf(MvcTestResult result) {
		return result.getResponse().getHeader(HttpHeaders.ETAG);
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

	private static RequestPostProcessor cashier() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER);
	}

}
