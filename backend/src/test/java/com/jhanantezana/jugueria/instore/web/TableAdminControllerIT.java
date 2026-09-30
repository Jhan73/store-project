package com.jhanantezana.jugueria.instore.web;

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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TableAdminControllerIT {

	static final String TABLES = "/api/v1/admin/tables";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@AfterEach
	void cleanUp() {
		InstoreTables.clean(jdbc);
	}

	@Test
	void createsATableWithAnAreaAndAnOrder() {
		var name = "Mesa-" + UUID.randomUUID();

		var result = create(name, "Terrace", 4);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(result).headers()
			.hasHeaderSatisfying(HttpHeaders.LOCATION,
					values -> assertThat(values).singleElement().asString().startsWith(TABLES + "/"));
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo(name);
		assertThat(result).bodyJson().extractingPath("$.area").isEqualTo("Terrace");
		assertThat(result).bodyJson().extractingPath("$.displayOrder").isEqualTo(4);
		assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"0\"");
	}

	@Test
	void anOmittedAreaAndOrderDefaultToNoAreaAndZero() {
		var result = mvc.post()
			.uri(TABLES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Mesa-%s\"}".formatted(UUID.randomUUID()))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.area").isNull();
		assertThat(result).bodyJson().extractingPath("$.displayOrder").isEqualTo(0);
	}

	@Test
	void aBlankAreaIsStoredAsNoArea() {
		var result = create("Mesa-" + UUID.randomUUID(), "   ", 0);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.area").isNull();
	}

	@Test
	void rejectsADuplicateNameIgnoringCase() {
		var name = "Mesa-" + UUID.randomUUID();
		create(name, null, 0);

		var result = create(name.toLowerCase(), "Other", 1);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("instore.table-name-already-used");
	}

	@Test
	void rejectsInvalidInput() {
		assertThat(create(" ", null, 0)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(create("x".repeat(61), null, 0)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(create("Mesa-" + UUID.randomUUID(), "x".repeat(61), 0)).hasStatus(HttpStatus.BAD_REQUEST);
		var negative = create("Mesa-" + UUID.randomUUID(), null, -1);
		assertThat(negative).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(negative).bodyJson().extractingPath("$.code").isEqualTo("common.validation-failed");
	}

	@Test
	void rejectsAnOrderBeyondTheColumnInsteadOfFailing() {
		var result = mvc.post()
			.uri(TABLES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Mesa-%s\",\"displayOrder\":99999999999}".formatted(UUID.randomUUID()))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void listsEveryTableInDisplayOrderIncludingInactiveOnes() {
		var second = create("B-" + UUID.randomUUID(), null, 2);
		var first = create("A-" + UUID.randomUUID(), "Bar", 1);
		deactivate(idOf(second), etagOf(second));

		var result = mvc.get().uri(TABLES).with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$[*].id").asList().containsExactly(idOf(first), idOf(second));
		assertThat(result).bodyJson().extractingPath("$[1].active").isEqualTo(false);
		assertThat(result).bodyJson().extractingPath("$[0].etag").isEqualTo("\"0\"");
	}

	@Test
	void updatesATableWithIfMatch() {
		var created = create("Mesa-" + UUID.randomUUID(), "Terrace", 0);
		var newName = "Barra-" + UUID.randomUUID();

		var result = update(idOf(created), newName, null, 5, etagOf(created));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo(newName);
		assertThat(result).bodyJson().extractingPath("$.area").isNull();
		assertThat(result).bodyJson().extractingPath("$.displayOrder").isEqualTo(5);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"1\"");
		assertThat(etagOf(result)).isEqualTo("\"1\"");
	}

	@Test
	void anUpdateMayKeepItsOwnName() {
		var name = "Mesa-" + UUID.randomUUID();
		var created = create(name, null, 0);

		var result = update(idOf(created), name.toUpperCase(), "Bar", 0, etagOf(created));

		assertThat(result).hasStatusOk();
	}

	@Test
	void anUpdateCannotTakeAnotherTablesName() {
		var name = "Mesa-" + UUID.randomUUID();
		create(name, null, 0);
		var other = create("Other-" + UUID.randomUUID(), null, 1);

		var result = update(idOf(other), name, null, 1, etagOf(other));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("instore.table-name-already-used");
	}

	@Test
	void anUpdateMustCarryTheDisplayOrder() {
		var created = create("Mesa-" + UUID.randomUUID(), null, 0);

		var result = mvc.put()
			.uri(TABLES + "/" + idOf(created))
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Mesa-%s\"}".formatted(UUID.randomUUID()))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.validation-failed");
	}

	@Test
	void rejectsAnUpdateWithoutIfMatch() {
		var created = create("Mesa-" + UUID.randomUUID(), null, 0);

		var result = mvc.put()
			.uri(TABLES + "/" + idOf(created))
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"x\",\"displayOrder\":0}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void aStaleUpdateIsRejectedAndTheWinningChangeIsKept() {
		var created = create("Mesa-" + UUID.randomUUID(), null, 0);
		var id = idOf(created);
		var staleEtag = etagOf(created);
		var winner = "Winner-" + UUID.randomUUID();
		update(id, winner, null, 0, staleEtag);

		var late = update(id, "Late-" + UUID.randomUUID(), null, 0, staleEtag);

		assertThat(late).hasStatus(HttpStatus.PRECONDITION_FAILED);
		var current = mvc.get().uri(TABLES).with(admin()).exchange();
		assertThat(current).bodyJson().extractingPath("$[?(@.id=='" + id + "')].name").asList().containsExactly(winner);
	}

	@Test
	void deactivatesAndReactivatesATable() {
		var created = create("Mesa-" + UUID.randomUUID(), null, 0);
		var id = idOf(created);

		var deactivated = deactivate(id, etagOf(created));
		assertThat(deactivated).hasStatusOk();
		assertThat(deactivated).bodyJson().extractingPath("$.active").isEqualTo(false);

		var reactivated = reactivate(id, etagOf(deactivated));
		assertThat(reactivated).hasStatusOk();
		assertThat(reactivated).bodyJson().extractingPath("$.active").isEqualTo(true);
	}

	@Test
	void deactivatingAnInactiveTableChangesNothing() {
		var created = create("Mesa-" + UUID.randomUUID(), null, 0);
		var deactivated = deactivate(idOf(created), etagOf(created));

		var again = deactivate(idOf(created), etagOf(deactivated));

		assertThat(again).hasStatusOk();
		assertThat(etagOf(again)).isEqualTo(etagOf(deactivated));
	}

	@Test
	void reactivatingAnActiveTableChangesNothing() {
		var created = create("Mesa-" + UUID.randomUUID(), null, 0);

		var result = reactivate(idOf(created), etagOf(created));

		assertThat(result).hasStatusOk();
		assertThat(etagOf(result)).isEqualTo(etagOf(created));
	}

	@Test
	void rejectsAStatusChangeWithoutIfMatch() {
		var created = create("Mesa-" + UUID.randomUUID(), null, 0);

		assertThat(mvc.post().uri(TABLES + "/" + idOf(created) + "/deactivate").with(admin()).exchange())
			.hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(mvc.post().uri(TABLES + "/" + idOf(created) + "/reactivate").with(admin()).exchange())
			.hasStatus(HttpStatus.PRECONDITION_REQUIRED);
	}

	@Test
	void reportsAnUnknownTable() {
		var id = UUID.randomUUID().toString();

		var updated = update(id, "Ghost", null, 0, "\"0\"");
		var deactivated = deactivate(id, "\"0\"");
		var reactivated = reactivate(id, "\"0\"");

		for (var result : new MvcTestResult[] { updated, deactivated, reactivated }) {
			assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
			assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("instore.table-not-found");
		}
	}

	@Test
	void rejectsEveryEndpointWhenAnonymous() {
		var id = UUID.randomUUID();
		var body = "{\"name\":\"x\",\"displayOrder\":0}";

		assertThat(mvc.get().uri(TABLES).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(TABLES).contentType(MediaType.APPLICATION_JSON).content(body).exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.put()
			.uri(TABLES + "/" + id)
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(TABLES + "/" + id + "/deactivate").header(HttpHeaders.IF_MATCH, "\"0\"").exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(TABLES + "/" + id + "/reactivate").header(HttpHeaders.IF_MATCH, "\"0\"").exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsEveryEndpointForNonAdminRoles() {
		var id = UUID.randomUUID();
		var body = "{\"name\":\"x\",\"displayOrder\":0}";
		for (var role : new Role[] { Role.SERVER, Role.CASHIER, Role.CUSTOMER }) {
			var user = AuthenticatedAs.user(UUID.randomUUID(), role);
			assertThat(mvc.get().uri(TABLES).with(user).exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post().uri(TABLES).with(user).contentType(MediaType.APPLICATION_JSON).content(body).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.put()
				.uri(TABLES + "/" + id)
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body)
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post()
				.uri(TABLES + "/" + id + "/deactivate")
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post()
				.uri(TABLES + "/" + id + "/reactivate")
				.with(user)
				.header(HttpHeaders.IF_MATCH, "\"0\"")
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
		}
	}

	private MvcTestResult create(String name, String area, int displayOrder) {
		return mvc.post()
			.uri(TABLES)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\",\"area\":%s,\"displayOrder\":%d}".formatted(name, quoted(area), displayOrder))
			.exchange();
	}

	private MvcTestResult update(String id, String name, String area, int displayOrder, String etag) {
		return mvc.put()
			.uri(TABLES + "/" + id)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\",\"area\":%s,\"displayOrder\":%d}".formatted(name, quoted(area), displayOrder))
			.exchange();
	}

	private MvcTestResult deactivate(String id, String etag) {
		return mvc.post()
			.uri(TABLES + "/" + id + "/deactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.exchange();
	}

	private MvcTestResult reactivate(String id, String etag) {
		return mvc.post()
			.uri(TABLES + "/" + id + "/reactivate")
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.exchange();
	}

	private static String quoted(String value) {
		return value == null ? "null" : "\"" + value + "\"";
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
