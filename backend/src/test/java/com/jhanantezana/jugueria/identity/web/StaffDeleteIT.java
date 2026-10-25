package com.jhanantezana.jugueria.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.identity.UserDeleted;
import com.jhanantezana.jugueria.identity.internal.RefreshToken;
import com.jhanantezana.jugueria.identity.internal.RefreshTokenRepository;
import com.jhanantezana.jugueria.identity.internal.SetPasswordTokenRepository;
import com.jhanantezana.jugueria.identity.internal.UserAccount;
import com.jhanantezana.jugueria.identity.internal.UserAccountRepository;
import com.jhanantezana.jugueria.identity.internal.security.RefreshTokens;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class StaffDeleteIT {

	static final String STAFF = "/api/v1/staff";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	SetPasswordTokenRepository setPasswordTokens;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@AfterEach
	void cleanUp() {
		refreshTokens.deleteAll();
		setPasswordTokens.deleteAll();
		accounts.deleteAll();
	}

	@Test
	void deletesAnInvitationNobodyAcceptedWithItsLinkAndPublishesItsSnapshot() {
		anAdmin();
		var invited = invite("Maria", "maria@jugueria.pe");

		var result = delete(invited, admin());

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(accounts.findById(invited)).isEmpty();
		assertThat(setPasswordTokens.count()).isZero();
		assertThat(events.stream(UserDeleted.class)).singleElement().satisfies(event -> {
			assertThat(event.userId()).isEqualTo(invited);
			assertThat(event.username()).isEqualTo("Maria");
			assertThat(event.role()).isEqualTo(Role.CASHIER);
		});
	}

	@Test
	void anInvitationThatWasDeactivatedCanStillBeDeleted() {
		anAdmin();
		var invited = invite("Maria", "maria@jugueria.pe");
		mvc.post().uri(STAFF + "/" + invited + "/deactivate").with(admin()).exchange();

		assertThat(delete(invited, admin())).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void refusesAnAccountThatSetItsPassword() {
		anAdmin();
		var invited = invite("Maria", "maria@jugueria.pe");
		jdbc.sql("update identity.set_password_token set used_at = now() where user_id = ?").param(invited).update();

		var result = delete(invited, admin());

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("identity.staff-account-in-use");
		assertThat(accounts.findById(invited)).isPresent();
		assertThat(events.stream(UserDeleted.class)).isEmpty();
	}

	@Test
	void refusesAnAccountThatEverHadASession() {
		anAdmin();
		var invited = invite("Maria", "maria@jugueria.pe");
		refreshTokens.save(new RefreshToken(UUID.randomUUID(), invited, RefreshTokens.hash(RefreshTokens.newRawToken()),
				Instant.now(), Instant.now().plusSeconds(3600), Instant.now()));

		assertThat(delete(invited, admin())).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void refusesToDeleteTheOnlyActiveAdmin() {
		var onlyAdmin = anAdmin();

		var result = delete(onlyAdmin, AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN));

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("identity.last-active-admin-required");
	}

	@Test
	void anAdminCannotDeleteTheirOwnAccount() {
		var self = anAdmin();

		var result = delete(self, AuthenticatedAs.user(self, Role.ADMIN));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("identity.cannot-modify-own-account");
	}

	@Test
	void theListSaysWhichAccountsCanBeDeleted() {
		anAdmin();
		var pending = invite("Pending", "pending@jugueria.pe");
		var active = invite("Active", "active@jugueria.pe");
		jdbc.sql("update identity.set_password_token set used_at = now() where user_id = ?").param(active).update();

		var result = mvc.get().uri(STAFF + "?size=100").with(admin()).exchange();

		assertThat(result).bodyJson()
			.extractingPath("$.content[?(@.id=='%s')].deletable".formatted(pending))
			.asList()
			.containsExactly(true);
		assertThat(result).bodyJson()
			.extractingPath("$.content[?(@.id=='%s')].deletable".formatted(active))
			.asList()
			.containsExactly(false);
	}

	@Test
	void aNewInvitationSaysItCanBeDeleted() {
		anAdmin();

		var result = mvc.post()
			.uri(STAFF)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"Maria\",\"email\":\"maria@jugueria.pe\",\"role\":\"CASHIER\"}")
			.exchange();

		assertThat(result).bodyJson().extractingPath("$.deletable").isEqualTo(true);
	}

	@Test
	void reportsAnUnknownAccount() {
		anAdmin();

		var result = delete(UUID.randomUUID(), admin());

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.not-found");
	}

	@Test
	void onlyAnAdminMayDelete() {
		anAdmin();
		var invited = invite("Maria", "maria@jugueria.pe");

		var result = delete(invited, AuthenticatedAs.user(UUID.randomUUID(), Role.CASHIER));

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(accounts.findById(invited)).isPresent();
	}

	private UUID anAdmin() {
		var account = new UserAccount("admin@jugueria.pe", "hash", Role.ADMIN, Instant.now(), true);
		account.rename("Owner", Instant.now());
		return accounts.save(account).getId();
	}

	private UUID invite(String username, String email) {
		var result = mvc.post()
			.uri(STAFF)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"%s\",\"email\":\"%s\",\"role\":\"CASHIER\"}".formatted(username, email))
			.exchange();
		return UUID.fromString(assertThat(result).bodyJson().extractingPath("$.id").actual().toString());
	}

	private MvcTestResult delete(UUID id, RequestPostProcessor auth) {
		return mvc.delete().uri(STAFF + "/" + id).with(auth).exchange();
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
