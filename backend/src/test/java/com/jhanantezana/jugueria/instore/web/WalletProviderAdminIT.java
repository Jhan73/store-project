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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.instore.WalletProviderChangeKind;
import com.jhanantezana.jugueria.instore.WalletProviderChanged;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class WalletProviderAdminIT {

	static final String ADMIN_PROVIDERS = "/api/v1/admin/wallet-providers";

	static final String PROVIDERS = "/api/v1/wallet-providers";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@AfterEach
	void cleanUp() {
		InstoreTables.clean(jdbc);
	}

	@Test
	void startsWithYapeAndPlin() {
		var result = mvc.get().uri(ADMIN_PROVIDERS).with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$[*].name").asList().containsExactly("Plin", "Yape");
	}

	@Test
	void createsAProviderWithAnEtagAndALocation() {
		var name = "Wallet-" + UUID.randomUUID();

		var result = create(name);

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).headers().hasHeaderSatisfying(HttpHeaders.ETAG, values -> assertThat(values).singleElement());
		assertThat(result).headers()
			.hasHeaderSatisfying(HttpHeaders.LOCATION,
					values -> assertThat(values).singleElement().asString().startsWith(ADMIN_PROVIDERS + "/"));
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo(name);
		assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.etag").isEqualTo("\"0\"");
		assertThat(events.stream(WalletProviderChanged.class)).singleElement().satisfies(changed -> {
			assertThat(changed.kind()).isEqualTo(WalletProviderChangeKind.CREATED);
			assertThat(changed.before()).isNull();
			assertThat(changed.after().name()).isEqualTo(name);
		});
	}

	@Test
	void trimsTheNameAndRejectsInvalidInput() {
		var created = create("  Tunki-" + UUID.randomUUID() + "  ");

		assertThat(created).hasStatus(HttpStatus.CREATED);
		assertThat(created).bodyJson().extractingPath("$.name").asString().startsWith("Tunki-").doesNotEndWith(" ");
		assertThat(create(" ")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(create("x".repeat(61))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void rejectsADuplicateActiveNameIgnoringCase() {
		var name = "Wallet-" + UUID.randomUUID();
		create(name);

		var result = create(name.toLowerCase());

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("instore.wallet-provider-name-already-used");
	}

	@Test
	void renamesWithIfMatchAndPublishesTheChange() {
		var created = create("Wallet-" + UUID.randomUUID());
		var newName = "Renamed-" + UUID.randomUUID();

		var result = rename(idOf(created), newName, etagOf(created));

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo(newName);
		assertThat(etagOf(result)).isEqualTo("\"1\"");
		assertThat(events.stream(WalletProviderChanged.class)
			.filter(changed -> changed.kind() == WalletProviderChangeKind.UPDATED)).singleElement()
			.satisfies(changed -> assertThat(changed.before().name()).startsWith("Wallet-"));
	}

	@Test
	void aRenameCannotTakeAnotherActiveProvidersName() {
		var name = "Wallet-" + UUID.randomUUID();
		create(name);
		var other = create("Other-" + UUID.randomUUID());

		var result = rename(idOf(other), name, etagOf(other));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("instore.wallet-provider-name-already-used");
	}

	@Test
	void rejectsAnUpdateWithoutOrWithAStaleIfMatch() {
		var created = create("Wallet-" + UUID.randomUUID());
		var missing = mvc.put()
			.uri(ADMIN_PROVIDERS + "/" + idOf(created))
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"X\"}")
			.exchange();

		assertThat(missing).hasStatus(HttpStatus.PRECONDITION_REQUIRED);
		assertThat(rename(idOf(created), "Y-" + UUID.randomUUID(), "\"7\"")).hasStatus(HttpStatus.PRECONDITION_FAILED);
	}

	@Test
	void anUnknownProviderIsNotFound() {
		var result = rename(UUID.randomUUID().toString(), "Nope", "\"0\"");

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("instore.wallet-provider-not-found");
	}

	@Test
	void deactivatesAndReactivatesWithoutAnyUsageGuard() {
		var created = create("Wallet-" + UUID.randomUUID());

		var off = post(idOf(created), "deactivate", etagOf(created));
		var on = post(idOf(created), "reactivate", etagOf(off));

		assertThat(off).hasStatusOk();
		assertThat(off).bodyJson().extractingPath("$.active").isEqualTo(false);
		assertThat(on).hasStatusOk();
		assertThat(on).bodyJson().extractingPath("$.active").isEqualTo(true);
		assertThat(events.stream(WalletProviderChanged.class).map(WalletProviderChanged::kind)).containsExactly(
				WalletProviderChangeKind.CREATED, WalletProviderChangeKind.DEACTIVATED,
				WalletProviderChangeKind.ACTIVATED);
	}

	@Test
	void deactivatingAnInactiveProviderChangesNothing() {
		var created = create("Wallet-" + UUID.randomUUID());
		var off = post(idOf(created), "deactivate", etagOf(created));

		var again = post(idOf(created), "deactivate", etagOf(off));

		assertThat(again).hasStatusOk();
		assertThat(etagOf(again)).isEqualTo(etagOf(off));
		assertThat(events.stream(WalletProviderChanged.class)
			.filter(changed -> changed.kind() == WalletProviderChangeKind.DEACTIVATED)).hasSize(1);
	}

	@Test
	void anInactiveNameCanBeReusedButItsReactivationThenConflicts() {
		var name = "Wallet-" + UUID.randomUUID();
		var first = create(name);
		var off = post(idOf(first), "deactivate", etagOf(first));
		assertThat(create(name)).hasStatus(HttpStatus.CREATED);

		var result = post(idOf(first), "reactivate", etagOf(off));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("instore.wallet-provider-name-already-used");
	}

	@Test
	void thereIsNoDelete() {
		var created = create("Wallet-" + UUID.randomUUID());

		var result = mvc.delete()
			.uri(ADMIN_PROVIDERS + "/" + idOf(created))
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etagOf(created))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
	}

	@Test
	void everyAdminCommandLeavesAnAuditRow() {
		var created = create("Wallet-" + UUID.randomUUID());
		var renamed = rename(idOf(created), "Renamed-" + UUID.randomUUID(), etagOf(created));
		var off = post(idOf(created), "deactivate", etagOf(renamed));
		post(idOf(created), "reactivate", etagOf(off));

		var actions = jdbc.sql("select action from audit.audit_log where entity_id = ? order by occurred_at, action")
			.param(UUID.fromString(idOf(created)))
			.query(String.class)
			.list();

		assertThat(actions).containsExactlyInAnyOrder("WALLET_PROVIDER_CREATED", "WALLET_PROVIDER_UPDATED",
				"WALLET_PROVIDER_DEACTIVATED", "WALLET_PROVIDER_REACTIVATED");
		var label = jdbc.sql("select entity_label from audit.audit_log where entity_id = ? and action = 'WALLET_PROVIDER_UPDATED'")
			.param(UUID.fromString(idOf(created)))
			.query(String.class)
			.single();
		assertThat(label).startsWith("Renamed-");
	}

	@Test
	void onlyAdminsManageProviders() {
		var created = create("Wallet-" + UUID.randomUUID());
		var id = idOf(created);
		var etag = etagOf(created);

		for (var role : new Role[] { Role.CASHIER, Role.SERVER, Role.KITCHEN, Role.CUSTOMER }) {
			var user = AuthenticatedAs.user(UUID.randomUUID(), role);
			assertThat(mvc.get().uri(ADMIN_PROVIDERS).with(user).exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post()
				.uri(ADMIN_PROVIDERS)
				.with(user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"X\"}")
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.put()
				.uri(ADMIN_PROVIDERS + "/" + id)
				.with(user)
				.header(HttpHeaders.IF_MATCH, etag)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"X\"}")
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post()
				.uri(ADMIN_PROVIDERS + "/" + id + "/deactivate")
				.with(user)
				.header(HttpHeaders.IF_MATCH, etag)
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.post()
				.uri(ADMIN_PROVIDERS + "/" + id + "/reactivate")
				.with(user)
				.header(HttpHeaders.IF_MATCH, etag)
				.exchange()).hasStatus(HttpStatus.FORBIDDEN);
		}
		assertThat(mvc.get().uri(ADMIN_PROVIDERS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void cashiersAndAdminsListOnlyTheActiveProviders() {
		var created = create("Wallet-" + UUID.randomUUID());
		post(idOf(created), "deactivate", etagOf(created));

		for (var role : new Role[] { Role.CASHIER, Role.ADMIN }) {
			var result = mvc.get().uri(PROVIDERS).with(AuthenticatedAs.user(UUID.randomUUID(), role)).exchange();

			assertThat(result).hasStatusOk();
			assertThat(result).bodyJson().extractingPath("$[*].name").asList().containsExactly("Plin", "Yape");
			assertThat(result).bodyJson().extractingPath("$[0].id").isEqualTo(InstoreTables.PLIN.toString());
		}
		for (var role : new Role[] { Role.SERVER, Role.KITCHEN, Role.CUSTOMER }) {
			assertThat(mvc.get().uri(PROVIDERS).with(AuthenticatedAs.user(UUID.randomUUID(), role)).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		}
		assertThat(mvc.get().uri(PROVIDERS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private MvcTestResult create(String name) {
		return mvc.post()
			.uri(ADMIN_PROVIDERS)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\"}".formatted(name))
			.exchange();
	}

	private MvcTestResult rename(String id, String name, String etag) {
		return mvc.put()
			.uri(ADMIN_PROVIDERS + "/" + id)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\"}".formatted(name))
			.exchange();
	}

	private MvcTestResult post(String id, String action, String etag) {
		return mvc.post()
			.uri(ADMIN_PROVIDERS + "/" + id + "/" + action)
			.with(admin())
			.header(HttpHeaders.IF_MATCH, etag)
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
