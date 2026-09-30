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
import com.jhanantezana.jugueria.catalog.StationChanged;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.CatalogTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class StationControllerIT {

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
	void createsAStationAndPublishesTheChange() {
		var name = "Bar-" + UUID.randomUUID();

		var result = create(name);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.LOCATION,
				values -> assertThat(values).singleElement().asString().startsWith(STATIONS + "/"));
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo(name);
		assertThat(result).bodyJson().extractingPath("$.defaultStation").isEqualTo(false);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"0\"");
		assertThat(events.stream(StationChanged.class))
			.singleElement()
			.satisfies(event -> assertThat(event.kind()).isEqualTo(CatalogChangeKind.CREATED));
	}

	@Test
	void listsTheSeededDefaultStation() {
		var result = mvc.get().uri(STATIONS).with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$[?(@.name=='Main')].defaultStation").asList().containsExactly(true);
	}

	@Test
	void rejectsADuplicateNameIgnoringCase() {
		var name = "Bar-" + UUID.randomUUID();
		create(name);

		var result = create(name.toUpperCase());

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.station-name-already-used");
	}

	@Test
	void rejectsABlankName() {
		var result = create(" ");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.validation-failed");
	}

	@Test
	void renamesAStationWithIfMatch() {
		var created = create("Bar-" + UUID.randomUUID());
		var newName = "Grill-" + UUID.randomUUID();

		var result = rename(idOf(created), newName, etagOf(created));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo(newName);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"1\"");
		assertThat(events.stream(StationChanged.class).map(StationChanged::kind))
			.containsExactly(CatalogChangeKind.CREATED, CatalogChangeKind.UPDATED);
	}

	@Test
	void rejectsAnUpdateWithoutIfMatch() {
		var created = create("Bar-" + UUID.randomUUID());

		var result = mvc.put()
			.uri(STATIONS + "/" + idOf(created))
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Other\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void aStaleUpdateIsRejectedAndTheOtherAdminsChangeIsKept() {
		var created = create("Bar-" + UUID.randomUUID());
		var id = idOf(created);
		var staleEtag = etagOf(created);
		var winner = "Winner-" + UUID.randomUUID();
		rename(id, winner, staleEtag);

		var late = rename(id, "Late-" + UUID.randomUUID(), staleEtag);

		assertThat(late).hasStatus(HttpStatus.PRECONDITION_FAILED);
		assertThat(late).bodyJson().extractingPath("$.currentETag").isEqualTo("\"1\"");
		var current = mvc.get().uri(STATIONS).with(admin()).exchange();
		assertThat(current).bodyJson().extractingPath("$[?(@.id=='" + id + "')].name").asList().containsExactly(winner);
	}

	@Test
	void reportsAnUnknownStation() {
		var result = rename(UUID.randomUUID().toString(), "Ghost", "\"0\"");

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("catalog.station-not-found");
	}

	@Test
	void rejectsEveryEndpointWhenAnonymous() {
		var id = UUID.randomUUID();

		assertThat(mvc.get().uri(STATIONS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(STATIONS).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}").exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.put()
			.uri(STATIONS + "/" + id)
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"x\"}")
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsEveryEndpointForNonAdminRoles() {
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.CUSTOMER }) {
			var user = AuthenticatedAs.user(UUID.randomUUID(), role);
			assertThat(mvc.get().uri(STATIONS).with(user).exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post()
				.uri(STATIONS)
				.with(user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"x\"}")
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.put()
				.uri(STATIONS + "/" + UUID.randomUUID())
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"x\"}")
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
		}
	}

	private MvcTestResult create(String name) {
		return mvc.post()
			.uri(STATIONS)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\"}".formatted(name))
			.exchange();
	}

	private MvcTestResult rename(String id, String name, String etag) {
		return mvc.put()
			.uri(STATIONS + "/" + id)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\"}".formatted(name))
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
