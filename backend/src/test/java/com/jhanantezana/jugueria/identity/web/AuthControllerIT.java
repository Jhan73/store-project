package com.jhanantezana.jugueria.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.identity.internal.UserAccount;
import com.jhanantezana.jugueria.identity.internal.UserAccountRepository;
import com.jhanantezana.jugueria.shared.Role;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerIT {

	static final String LOGIN = "/api/v1/auth/login";

	static final String PASSWORD = "correct-horse-battery-staple";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	PasswordEncoder passwordEncoder;

	@AfterEach
	void cleanUp() {
		accounts.deleteAll();
	}

	@Test
	void issuesAnAccessTokenForTheRightCredentials() {
		var account = accounts.save(new UserAccount("cashier@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER, Instant.now()));

		var result = login("cashier@jugueria.pe", PASSWORD);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.userId").isEqualTo(account.getId().toString());
		assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("CASHIER");
		assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
	}

	@Test
	void rejectsAnUnknownEmailWithoutRevealingIt() {
		assertInvalidCredentials(login("nobody@jugueria.pe", PASSWORD));
	}

	@Test
	void rejectsTheWrongPasswordWithTheSameCodeAsAnUnknownEmail() {
		accounts.save(new UserAccount("cashier@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER, Instant.now()));

		assertInvalidCredentials(login("cashier@jugueria.pe", "wrong-password"));
	}

	@Test
	void rejectsAnInactiveAccountWithTheSameCode() {
		accounts.save(new UserAccount("cashier@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER, Instant.now(), false));

		assertInvalidCredentials(login("cashier@jugueria.pe", PASSWORD));
	}

	@Test
	void locksTheAccountAfterFiveFailedAttemptsAndAnswersEvenTheCorrectPassword() {
		accounts.save(new UserAccount("cashier@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER, Instant.now()));

		for (int i = 0; i < 5; i++) {
			assertInvalidCredentials(login("cashier@jugueria.pe", "wrong-password"));
		}
		var result = login("cashier@jugueria.pe", PASSWORD);

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.account-locked");
		assertThat(result).bodyJson().extractingPath("$.lockedUntil").isNotNull();
	}

	@Test
	void resetsTheFailedAttemptCounterOnASuccessfulLogin() {
		accounts.save(new UserAccount("cashier@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER, Instant.now()));
		login("cashier@jugueria.pe", "wrong-password");
		login("cashier@jugueria.pe", "wrong-password");

		assertThat(login("cashier@jugueria.pe", PASSWORD)).hasStatusOk();

		// Two more failures after the reset must not be enough to lock the account (max is 5).
		login("cashier@jugueria.pe", "wrong-password");
		assertThat(login("cashier@jugueria.pe", "wrong-password"))
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("auth.invalid-credentials");
	}

	@Test
	void rejectsAMalformedLoginRequest() {
		var result = mvc.post()
			.uri(LOGIN)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"not-an-email\",\"password\":\"\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.validation-failed");
	}

	private MvcTestResult login(String email, String password) {
		return mvc.post()
			.uri(LOGIN)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
			.exchange();
	}

	private static void assertInvalidCredentials(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.invalid-credentials");
	}

}
