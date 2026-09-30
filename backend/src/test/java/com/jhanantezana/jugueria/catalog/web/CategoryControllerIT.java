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
import com.jhanantezana.jugueria.catalog.CategoryChanged;
import com.jhanantezana.jugueria.catalog.CatalogChangeKind;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.CatalogTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class CategoryControllerIT {

	static final String CATEGORIES = "/api/v1/admin/categories";

	static final String STATIONS = "/api/v1/admin/stations";

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
	void createsACategoryOnTheDefaultStationWhenNoneIsGiven() {
		var name = "Juices-" + UUID.randomUUID();

		var result = create(name, null, 1);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo(name);
		assertThat(result).bodyJson().extractingPath("$.displayOrder").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.stationId").isEqualTo(defaultStationId());
		assertThat(events.stream(CategoryChanged.class))
			.singleElement()
			.satisfies(event -> assertThat(event.kind()).isEqualTo(CatalogChangeKind.CREATED));
	}

	@Test
	void createsACategoryOnAnExplicitStation() {
		var stationId = createStation("Bar-" + UUID.randomUUID());

		var result = create("Coffee-" + UUID.randomUUID(), stationId, 0);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.stationId").isEqualTo(stationId);
	}

	@Test
	void rejectsAnUnknownStation() {
		var result = create("Juices-" + UUID.randomUUID(), UUID.randomUUID().toString(), 0);

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.unknown-station");
	}

	@Test
	void rejectsADuplicateNameIgnoringCase() {
		var name = "Juices-" + UUID.randomUUID();
		create(name, null, 0);

		var result = create(name.toLowerCase(), null, 1);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.category-name-already-used");
	}

	@Test
	void rejectsANegativeDisplayOrder() {
		var result = create("Juices-" + UUID.randomUUID(), null, -1);

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.validation-failed");
	}

	@Test
	void listsCategoriesInDisplayOrder() {
		var second = create("B-" + UUID.randomUUID(), null, 2);
		var first = create("A-" + UUID.randomUUID(), null, 1);

		var result = mvc.get().uri(CATEGORIES).with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$[*].id").asList().containsSequence(idOf(first), idOf(second));
	}

	@Test
	void updatesACategoryWithIfMatch() {
		var created = create("Juices-" + UUID.randomUUID(), null, 0);
		var stationId = createStation("Bar-" + UUID.randomUUID());
		var newName = "Smoothies-" + UUID.randomUUID();

		var result = update(idOf(created), newName, stationId, 5, etagOf(created));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo(newName);
		assertThat(result).bodyJson().extractingPath("$.stationId").isEqualTo(stationId);
		assertThat(result).bodyJson().extractingPath("$.displayOrder").isEqualTo(5);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"1\"");
		assertThat(events.stream(CategoryChanged.class).map(CategoryChanged::kind))
			.containsExactly(CatalogChangeKind.CREATED, CatalogChangeKind.UPDATED);
	}

	@Test
	void rejectsAnUpdateWithoutIfMatch() {
		var created = create("Juices-" + UUID.randomUUID(), null, 0);

		var result = mvc.put()
			.uri(CATEGORIES + "/" + idOf(created))
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"x\",\"stationId\":\"%s\",\"displayOrder\":0}".formatted(defaultStationId()))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void aStaleUpdateIsRejectedAndTheOtherAdminsChangeIsKept() {
		var created = create("Juices-" + UUID.randomUUID(), null, 0);
		var id = idOf(created);
		var staleEtag = etagOf(created);
		var winner = "Winner-" + UUID.randomUUID();
		update(id, winner, defaultStationId(), 0, staleEtag);

		var late = update(id, "Late-" + UUID.randomUUID(), defaultStationId(), 0, staleEtag);

		assertThat(late).hasStatus(HttpStatus.PRECONDITION_FAILED);
		var current = mvc.get().uri(CATEGORIES).with(admin()).exchange();
		assertThat(current).bodyJson().extractingPath("$[?(@.id=='" + id + "')].name").asList().containsExactly(winner);
	}

	@Test
	void deactivatesAndReactivatesACategory() {
		var created = create("Juices-" + UUID.randomUUID(), null, 0);
		var id = idOf(created);

		var deactivated = mvc.post()
			.uri(CATEGORIES + "/" + id + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();
		assertThat(deactivated).hasStatusOk();
		assertThat(deactivated).bodyJson().extractingPath("$.active").isEqualTo(false);

		var reactivated = mvc.post()
			.uri(CATEGORIES + "/" + id + "/reactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(deactivated))
			.exchange();
		assertThat(reactivated).hasStatusOk();
		assertThat(reactivated).bodyJson().extractingPath("$.active").isEqualTo(true);
		assertThat(events.stream(CategoryChanged.class).map(CategoryChanged::kind)).containsExactly(
				CatalogChangeKind.CREATED, CatalogChangeKind.DEACTIVATED, CatalogChangeKind.ACTIVATED);
	}

	@Test
	void deactivatingAnInactiveCategoryChangesNothing() {
		var created = create("Juices-" + UUID.randomUUID(), null, 0);
		var id = idOf(created);
		var deactivated = mvc.post()
			.uri(CATEGORIES + "/" + id + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();

		var again = mvc.post()
			.uri(CATEGORIES + "/" + id + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(deactivated))
			.exchange();

		assertThat(again).hasStatusOk();
		assertThat(events.stream(CategoryChanged.class)).hasSize(2);
	}

	@Test
	void rejectsADeactivateWithoutIfMatch() {
		var created = create("Juices-" + UUID.randomUUID(), null, 0);

		var result = mvc.post().uri(CATEGORIES + "/" + idOf(created) + "/deactivate").with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void reportsAnUnknownCategory() {
		var result = update(UUID.randomUUID().toString(), "Ghost", defaultStationId(), 0, "\"0\"");

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.category-not-found");
	}

	@Test
	void rejectsEveryEndpointWhenAnonymous() {
		var id = UUID.randomUUID();
		var body = "{\"name\":\"x\",\"stationId\":\"" + UUID.randomUUID() + "\",\"displayOrder\":0}";

		assertThat(mvc.get().uri(CATEGORIES).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(CATEGORIES).contentType(MediaType.APPLICATION_JSON).content(body).exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.put()
			.uri(CATEGORIES + "/" + id)
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(CATEGORIES + "/" + id + "/deactivate").header(HttpHeaders.IF_MATCH, "\"0\"").exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(CATEGORIES + "/" + id + "/reactivate").header(HttpHeaders.IF_MATCH, "\"0\"").exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsEveryEndpointForNonAdminRoles() {
		var id = UUID.randomUUID();
		var body = "{\"name\":\"x\",\"stationId\":\"" + UUID.randomUUID() + "\",\"displayOrder\":0}";
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.CUSTOMER }) {
			var user = AuthenticatedAs.user(UUID.randomUUID(), role);
			assertThat(mvc.get().uri(CATEGORIES).with(user).exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post().uri(CATEGORIES).with(user).contentType(MediaType.APPLICATION_JSON).content(body).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.put()
				.uri(CATEGORIES + "/" + id)
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post()
				.uri(CATEGORIES + "/" + id + "/deactivate")
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post()
				.uri(CATEGORIES + "/" + id + "/reactivate")
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
		}
	}

	private String defaultStationId() {
		return jdbc.sql("select id from catalog.station where default_station").query(UUID.class).single().toString();
	}

	private String createStation(String name) {
		var result = mvc.post()
			.uri(STATIONS)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\"}".formatted(name))
			.exchange();
		return idOf(result);
	}

	private MvcTestResult create(String name, String stationId, int displayOrder) {
		var station = stationId == null ? "null" : "\"" + stationId + "\"";
		return mvc.post()
			.uri(CATEGORIES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\",\"stationId\":%s,\"displayOrder\":%d}".formatted(name, station, displayOrder))
			.exchange();
	}

	private MvcTestResult update(String id, String name, String stationId, int displayOrder, String etag) {
		return mvc.put()
			.uri(CATEGORIES + "/" + id)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\",\"stationId\":\"%s\",\"displayOrder\":%d}".formatted(name, stationId,
					displayOrder))
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
