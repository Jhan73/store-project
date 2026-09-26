package com.jhanantezana.jugueria.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.identity.internal.RefreshToken;
import com.jhanantezana.jugueria.identity.internal.RefreshTokenRepository;
import com.jhanantezana.jugueria.identity.internal.SetPasswordToken;
import com.jhanantezana.jugueria.identity.internal.SetPasswordTokenRepository;
import com.jhanantezana.jugueria.identity.internal.UserAccount;
import com.jhanantezana.jugueria.identity.internal.UserAccountRepository;
import com.jhanantezana.jugueria.identity.internal.security.RefreshTokens;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StaffControllerIT {

	static final String STAFF = "/api/v1/staff";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	SetPasswordTokenRepository setPasswordTokens;

	@AfterEach
	void cleanUp() {
		refreshTokens.deleteAll();
		setPasswordTokens.deleteAll();
		accounts.deleteAll();
	}

	@Test
	void createsAStaffAccountAsAdmin() {
		var result = create("New-Cashier@Jugueria.PE", Role.CASHIER, admin());

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).headers().hasHeaderSatisfying("Location", values -> assertThat(values).singleElement());
		assertThat(result).bodyJson().extractingPath("$.email").isEqualTo("new-cashier@jugueria.pe");
		assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("CASHIER");
		assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
	}

	@Test
	void rejectsCreateForANonAdminRole() {
		var result = create("cashier@jugueria.pe", Role.CASHIER, AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER));

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void rejectsCreateWhenAnonymous() {
		var result = mvc.post()
			.uri(STAFF)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"x@jugueria.pe\",\"role\":\"CASHIER\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void rejectsADuplicateEmail() {
		accounts.save(new UserAccount("dup@jugueria.pe", "hash", Role.CASHIER, Instant.now()));

		var result = create("dup@jugueria.pe", Role.CASHIER, admin());

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("identity.email-already-registered");
	}

	@Test
	void rejectsCreatingACustomerAsStaff() {
		var result = create("customer@jugueria.pe", Role.CUSTOMER, admin());

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("identity.invalid-staff-role");
	}

	@Test
	void listExcludesCustomerAccounts() {
		accounts.save(new UserAccount("staff@jugueria.pe", "hash", Role.CASHIER, Instant.now()));
		accounts.save(new UserAccount("customer@jugueria.pe", "hash", Role.CUSTOMER, Instant.now()));

		var result = mvc.get().uri(STAFF).with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
	}

	@Test
	void getReturns404ForACustomerAccount() {
		var customer = accounts.save(new UserAccount("customer@jugueria.pe", "hash", Role.CUSTOMER, Instant.now()));

		var result = mvc.get().uri(STAFF + "/" + customer.getId()).with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void getReturns404ForAnUnknownId() {
		var result = mvc.get().uri(STAFF + "/" + UUID.randomUUID()).with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void changesRoleAndRevokesExistingRefreshTokens() {
		var staff = accounts.save(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, Instant.now()));
		seedRefreshToken(staff.getId());

		var result = changeRole(staff.getId(), Role.SERVER, admin());

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("SERVER");
		assertThat(refreshTokens.findAll()).allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
	}

	@Test
	void rejectsChangingOnesOwnRole() {
		var self = accounts.save(new UserAccount("self-admin@jugueria.pe", "hash", Role.ADMIN, Instant.now()));

		var result = changeRole(self.getId(), Role.CASHIER, AuthenticatedAs.user(self.getId(), Role.ADMIN));

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("identity.cannot-modify-own-account");
	}

	@Test
	void rejectsDemotingTheLastActiveAdmin() {
		var onlyAdmin = accounts.save(new UserAccount("only-admin@jugueria.pe", "hash", Role.ADMIN, Instant.now()));

		var result = changeRole(onlyAdmin.getId(), Role.CASHIER, admin());

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("identity.last-active-admin-required");
	}

	@Test
	void deactivatesAndRevokesSessions() {
		var staff = accounts.save(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, Instant.now()));
		seedRefreshToken(staff.getId());

		var result = mvc.post().uri(STAFF + "/" + staff.getId() + "/deactivate").with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(false);
		assertThat(refreshTokens.findAll()).allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
	}

	@Test
	void rejectsDeactivatingOneself() {
		var self = accounts.save(new UserAccount("self-admin@jugueria.pe", "hash", Role.ADMIN, Instant.now()));

		var result = mvc.post()
			.uri(STAFF + "/" + self.getId() + "/deactivate")
			.with(AuthenticatedAs.user(self.getId(), Role.ADMIN))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("identity.cannot-modify-own-account");
	}

	@Test
	void rejectsDeactivatingTheLastActiveAdmin() {
		var onlyAdmin = accounts.save(new UserAccount("only-admin@jugueria.pe", "hash", Role.ADMIN, Instant.now()));

		var result = mvc.post().uri(STAFF + "/" + onlyAdmin.getId() + "/deactivate").with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("identity.last-active-admin-required");
	}

	@Test
	void reactivatesADeactivatedAccount() {
		var staff = accounts
			.save(new UserAccount("cashier@jugueria.pe", "hash", Role.CASHIER, Instant.now(), false));

		var result = mvc.post().uri(STAFF + "/" + staff.getId() + "/reactivate").with(admin()).exchange();

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
	}

	@Test
	void resendsTheSetPasswordLinkAndRevokesTheEarlierToken() {
		var staff = accounts.save(new UserAccount("resend@jugueria.pe", "unusable-hash", Role.CASHIER, Instant.now()));
		var oldRaw = RefreshTokens.newRawToken();
		var oldToken = setPasswordTokens.save(new SetPasswordToken(staff.getId(), RefreshTokens.hash(oldRaw), Instant.now(),
				Instant.now().plusSeconds(3600)));

		var result = mvc.post().uri(STAFF + "/" + staff.getId() + "/set-password-link").with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(setPasswordTokens.findById(oldToken.getId()).orElseThrow().getRevokedAt()).isNotNull();
		var remaining = setPasswordTokens.findAll().stream().filter(token -> !token.getId().equals(oldToken.getId())).toList();
		assertThat(remaining).hasSize(1);
		assertThat(remaining.getFirst().getRevokedAt()).isNull();
	}

	@Test
	void rejectsResendingOnesOwnSetPasswordLink() {
		var self = accounts.save(new UserAccount("self-admin-resend@jugueria.pe", "hash", Role.ADMIN, Instant.now()));

		var result = mvc.post()
			.uri(STAFF + "/" + self.getId() + "/set-password-link")
			.with(AuthenticatedAs.user(self.getId(), Role.ADMIN))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("identity.cannot-modify-own-account");
	}

	@ParameterizedTest
	@MethodSource("staffRoutes")
	void rejectsWhenAnonymous(RouteCase route) {
		var staff = accounts.save(new UserAccount("route-anon@jugueria.pe", "hash", Role.CASHIER, Instant.now()));

		var result = exchange(route, staff.getId(), null);

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@ParameterizedTest
	@MethodSource("staffRoutes")
	void rejectsForANonAdminRole(RouteCase route) {
		var staff = accounts.save(new UserAccount("route-denied@jugueria.pe", "hash", Role.CASHIER, Instant.now()));

		var result = exchange(route, staff.getId(), AuthenticatedAs.user(UUID.randomUUID(), Role.SERVER));

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
	}

	record RouteCase(String name, HttpMethod method, String pathTemplate, @Nullable String body) {
		@Override
		public String toString() {
			return name;
		}
	}

	static Stream<RouteCase> staffRoutes() {
		return Stream.of(
				new RouteCase("create", HttpMethod.POST, STAFF, "{\"email\":\"route@jugueria.pe\",\"role\":\"CASHIER\"}"),
				new RouteCase("list", HttpMethod.GET, STAFF, null),
				new RouteCase("get", HttpMethod.GET, STAFF + "/{id}", null),
				new RouteCase("changeRole", HttpMethod.PATCH, STAFF + "/{id}/role", "{\"role\":\"SERVER\"}"),
				new RouteCase("deactivate", HttpMethod.POST, STAFF + "/{id}/deactivate", null),
				new RouteCase("reactivate", HttpMethod.POST, STAFF + "/{id}/reactivate", null),
				new RouteCase("resendSetPasswordLink", HttpMethod.POST, STAFF + "/{id}/set-password-link", null));
	}

	private MvcTestResult exchange(RouteCase route, UUID id, @Nullable RequestPostProcessor auth) {
		var uri = route.pathTemplate().replace("{id}", id.toString());
		var builder = mvc.method(route.method()).uri(uri);
		if (route.body() != null) {
			builder = builder.contentType(MediaType.APPLICATION_JSON).content(route.body());
		}
		if (auth != null) {
			builder = builder.with(auth);
		}
		return builder.exchange();
	}

	private MvcTestResult create(String email, Role role, RequestPostProcessor auth) {
		return mvc.post()
			.uri(STAFF)
			.with(auth)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role))
			.exchange();
	}

	private MvcTestResult changeRole(UUID id, Role role, RequestPostProcessor auth) {
		return mvc.patch()
			.uri(STAFF + "/" + id + "/role")
			.with(auth)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"%s\"}".formatted(role))
			.exchange();
	}

	private void seedRefreshToken(UUID userId) {
		var raw = RefreshTokens.newRawToken();
		refreshTokens.save(new RefreshToken(UUID.randomUUID(), userId, RefreshTokens.hash(raw), Instant.now(),
				Instant.now().plusSeconds(3600), Instant.now()));
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
