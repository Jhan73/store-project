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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
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

	private MvcTestResult create(String email, Role role,
			org.springframework.test.web.servlet.request.RequestPostProcessor auth) {
		return mvc.post()
			.uri(STAFF)
			.with(auth)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role))
			.exchange();
	}

	private MvcTestResult changeRole(UUID id, Role role,
			org.springframework.test.web.servlet.request.RequestPostProcessor auth) {
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

	private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
