package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jhanantezana.jugueria.audit.internal.AuditLog;
import com.jhanantezana.jugueria.audit.internal.AuditLogRepository;
import com.jhanantezana.jugueria.identity.internal.SetPasswordToken;
import com.jhanantezana.jugueria.identity.internal.SetPasswordTokenRepository;
import com.jhanantezana.jugueria.identity.internal.UserAccount;
import com.jhanantezana.jugueria.identity.internal.UserAccountRepository;
import com.jhanantezana.jugueria.identity.internal.security.RefreshTokens;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;

// Proves the documented exception in backend/CLAUDE.md: audit's plain synchronous @EventListener
// runs inside the publisher's transaction, so a failed audit write rolls back the business change.
// Covers every command that currently audits a change to a user account.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditRollbackIT {

	static final String STAFF = "/api/v1/staff";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	SetPasswordTokenRepository setPasswordTokens;

	@MockitoBean
	AuditLogRepository auditLogs;

	@BeforeEach
	void auditWritesAlwaysFail() {
		when(auditLogs.save(any(AuditLog.class))).thenThrow(new RuntimeException("boom"));
	}

	@AfterEach
	void cleanUp() {
		setPasswordTokens.deleteAll();
		accounts.deleteAll();
	}

	@Test
	void anAuditWriteFailureRollsBackTheStaffAccountCreation() {
		var email = "rollback-" + UUID.randomUUID() + "@jugueria.pe";

		var result = mvc.post()
			.uri(STAFF)
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"role\":\"CASHIER\"}".formatted(email))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(accounts.findByEmailIgnoreCase(email)).isEmpty();
	}

	@Test
	void anAuditWriteFailureRollsBackAChangeRole() {
		var staff = accounts.save(new UserAccount(email(), "hash", Role.CASHIER, Instant.now()));

		var result = mvc.patch()
			.uri(STAFF + "/" + staff.getId() + "/role")
			.with(admin())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"SERVER\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(accounts.findById(staff.getId()).orElseThrow().getRole()).isEqualTo(Role.CASHIER);
	}

	@Test
	void anAuditWriteFailureRollsBackADeactivate() {
		var staff = accounts.save(new UserAccount(email(), "hash", Role.CASHIER, Instant.now()));

		var result = mvc.post().uri(STAFF + "/" + staff.getId() + "/deactivate").with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(accounts.findById(staff.getId()).orElseThrow().isActive()).isTrue();
	}

	@Test
	void anAuditWriteFailureRollsBackAReactivate() {
		var staff = accounts.save(new UserAccount(email(), "hash", Role.CASHIER, Instant.now(), false));

		var result = mvc.post().uri(STAFF + "/" + staff.getId() + "/reactivate").with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(accounts.findById(staff.getId()).orElseThrow().isActive()).isFalse();
	}

	@Test
	void anAuditWriteFailureRollsBackASetPasswordLinkReissue() {
		var staff = accounts.save(new UserAccount(email(), "hash", Role.CASHIER, Instant.now()));

		var result = mvc.post().uri(STAFF + "/" + staff.getId() + "/set-password-link").with(admin()).exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(setPasswordTokens.findAll()).isEmpty();
	}

	@Test
	void anAuditWriteFailureRollsBackASetPassword() {
		var staff = accounts.save(new UserAccount(email(), "unusable-hash", Role.CASHIER, Instant.now()));
		var raw = RefreshTokens.newRawToken();
		var token = setPasswordTokens
			.save(new SetPasswordToken(staff.getId(), RefreshTokens.hash(raw), Instant.now(),
					Instant.now().plus(Duration.ofHours(48))));

		var result = mvc.post()
			.uri("/api/v1/auth/set-password")
			.header("X-Requested-With", "XMLHttpRequest")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"%s\",\"newPassword\":\"a-brand-new-password\"}".formatted(raw))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(accounts.findById(staff.getId()).orElseThrow().getPasswordHash()).isEqualTo("unusable-hash");
		assertThat(setPasswordTokens.findById(token.getId()).orElseThrow().getUsedAt()).isNull();
	}

	private static String email() {
		return "rollback-" + UUID.randomUUID() + "@jugueria.pe";
	}

	private static RequestPostProcessor admin() {
		return AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN);
	}

}
