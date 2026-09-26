package com.jhanantezana.jugueria.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.identity.internal.RefreshToken;
import com.jhanantezana.jugueria.identity.internal.RefreshTokenRepository;
import com.jhanantezana.jugueria.identity.internal.UserAccount;
import com.jhanantezana.jugueria.identity.internal.UserAccountRepository;
import com.jhanantezana.jugueria.identity.internal.security.RefreshTokens;
import com.jhanantezana.jugueria.shared.Role;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerIT {

	static final String LOGIN = "/api/v1/auth/login";

	static final String REFRESH = "/api/v1/auth/refresh";

	static final String LOGOUT = "/api/v1/auth/logout";

	static final String PASSWORD = "correct-horse-battery-staple";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	PasswordEncoder passwordEncoder;

	@AfterEach
	void cleanUp() {
		refreshTokens.deleteAll();
		accounts.deleteAll();
	}

	@Test
	void issuesAnAccessTokenForTheRightCredentials() {
		var account = accounts
			.save(new UserAccount("cashier@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER, Instant.now()));

		var result = login("cashier@jugueria.pe", PASSWORD);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.userId").isEqualTo(account.getId().toString());
		assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("CASHIER");
		assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
		assertRefreshCookieIssued(result);
	}

	@Test
	void rejectsAnUnknownEmailWithoutRevealingIt() {
		assertInvalidCredentials(login("nobody@jugueria.pe", PASSWORD));
	}

	@Test
	void rejectsTheWrongPasswordWithTheSameCodeAsAnUnknownEmail() {
		accounts
			.save(new UserAccount("cashier@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER, Instant.now()));

		assertInvalidCredentials(login("cashier@jugueria.pe", "wrong-password"));
	}

	@Test
	void rejectsAnInactiveAccountWithTheSameCode() {
		accounts.save(new UserAccount("cashier@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER,
				Instant.now(), false));

		assertInvalidCredentials(login("cashier@jugueria.pe", PASSWORD));
	}

	@Test
	void locksTheAccountAfterFiveFailedAttemptsAndAnswersEvenTheCorrectPassword() {
		accounts
			.save(new UserAccount("cashier@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER, Instant.now()));

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
		accounts
			.save(new UserAccount("cashier@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER, Instant.now()));
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
	void oneFailureRightAfterTheLockExpiresDoesNotRelockTheAccount() {
		var account = accounts.save(new UserAccount("expired@jugueria.pe", passwordEncoder.encode(PASSWORD),
				Role.CASHIER, Instant.now(), 5, Instant.now().minus(Duration.ofSeconds(1))));

		assertInvalidCredentials(login("expired@jugueria.pe", "wrong-password"));

		var reloaded = accounts.findById(account.getId()).orElseThrow();
		assertThat(reloaded.getFailedAttempts()).isEqualTo(1);
		assertThat(reloaded.getLockedUntil()).isNull();
	}

	@Test
	void logsInWithTheCorrectPasswordOnceThePreviousLockHasExpired() {
		accounts.save(new UserAccount("expired@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER,
				Instant.now(), 5, Instant.now().minus(Duration.ofSeconds(1))));

		assertThat(login("expired@jugueria.pe", PASSWORD)).hasStatusOk();
	}

	@Test
	void locksAgainAfterMaxFailuresFollowingAnExpiredLock() {
		accounts.save(new UserAccount("expired@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER,
				Instant.now(), 5, Instant.now().minus(Duration.ofSeconds(1))));

		for (int i = 0; i < 5; i++) {
			assertInvalidCredentials(login("expired@jugueria.pe", "wrong-password"));
		}
		var result = login("expired@jugueria.pe", PASSWORD);

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.account-locked");
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

	@Test
	void refreshRotatesTheTokenAndReturnsANewAccessToken() {
		accounts
			.save(new UserAccount("cashier@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER, Instant.now()));
		var loginResult = login("cashier@jugueria.pe", PASSWORD);
		var firstRefreshToken = cookieValue(loginResult);

		var result = refresh(firstRefreshToken);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.accessToken").isNotNull();
		assertRefreshCookieIssued(result);
		assertThat(cookieValue(result)).isNotEqualTo(firstRefreshToken);
	}

	@Test
	void refreshRejectsAMissingCookie() {
		assertInvalidRefreshToken(mvc.post().uri(REFRESH).header("X-Requested-With", "XMLHttpRequest").exchange());
	}

	@Test
	void refreshRejectsAnUnknownToken() {
		assertInvalidRefreshToken(refresh("not-a-real-token"));
	}

	@Test
	void refreshRejectsWithoutTheRequestedWithHeader() {
		var result = mvc.post().uri(REFRESH).cookie(new Cookie(RefreshTokenCookie.NAME, "whatever")).exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.malformed-request");
	}

	@Test
	void refreshRejectsAnExpiredToken() {
		var user = accounts.save(new UserAccount("expired-refresh@jugueria.pe", "hash", Role.CASHIER, Instant.now()));
		var raw = seedToken(user.getId(), Instant.now().minus(Duration.ofDays(31)), Instant.now().minusSeconds(1));

		assertInvalidRefreshToken(refresh(raw));
	}

	@Test
	void refreshRejectsAReusedTokenAndRevokesTheWholeFamily() {
		var user = accounts.save(new UserAccount("reuse@jugueria.pe", "hash", Role.CASHIER, Instant.now()));
		var raw = seedToken(user.getId(), Instant.now(), Instant.now().plus(Duration.ofDays(30)));
		var familyId = refreshTokens.findByTokenHash(RefreshTokens.hash(raw)).orElseThrow().getFamilyId();
		refresh(raw); // first use rotates it

		assertInvalidRefreshToken(refresh(raw));

		assertThat(refreshTokens.findByTokenHash(RefreshTokens.hash(raw)).orElseThrow().getRevokedAt()).isNotNull();
		var stillInFamily = refreshTokens.findAll()
			.stream()
			.filter(token -> token.getFamilyId().equals(familyId))
			.toList();
		assertThat(stillInFamily).allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
	}

	@Test
	void refreshRejectsAnInactiveUsersToken() {
		var user = accounts
			.save(new UserAccount("inactive@jugueria.pe", "hash", Role.CASHIER, Instant.now(), false));
		var raw = seedToken(user.getId(), Instant.now(), Instant.now().plus(Duration.ofDays(30)));

		assertInvalidRefreshToken(refresh(raw));
	}

	@Test
	void refreshRejectsALockedUsersToken() {
		var user = accounts.save(new UserAccount("locked-refresh@jugueria.pe", "hash", Role.CASHIER, Instant.now(), 5,
				Instant.now().plus(Duration.ofMinutes(15))));
		var raw = seedToken(user.getId(), Instant.now(), Instant.now().plus(Duration.ofDays(30)));

		assertInvalidRefreshToken(refresh(raw));
	}

	@Test
	void onlyOneOfTwoConcurrentRefreshesWithTheSameTokenSucceeds() throws InterruptedException {
		var user = accounts.save(new UserAccount("concurrent@jugueria.pe", "hash", Role.CASHIER, Instant.now()));
		var raw = seedToken(user.getId(), Instant.now(), Instant.now().plus(Duration.ofDays(30)));
		var attempts = 2;
		var successes = new AtomicInteger();
		var ready = new CountDownLatch(attempts);
		var go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		try {
			for (int i = 0; i < attempts; i++) {
				pool.submit(() -> {
					ready.countDown();
					await(go);
					if (refresh(raw).getResponse().getStatus() == HttpStatus.OK.value()) {
						successes.incrementAndGet();
					}
				});
			}
			ready.await();
			go.countDown();
			pool.shutdown();
			pool.awaitTermination(10, TimeUnit.SECONDS);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(successes.get()).isEqualTo(1);
	}

	@Test
	void logoutRevokesTheFamilyAndClearsTheCookie() {
		accounts
			.save(new UserAccount("logout@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER, Instant.now()));
		var raw = cookieValue(login("logout@jugueria.pe", PASSWORD));

		var result = logout(raw);

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(result).headers()
			.hasHeaderSatisfying(HttpHeaders.SET_COOKIE,
					values -> assertThat(values).singleElement().asString().contains("Max-Age=0"));
		assertInvalidRefreshToken(refresh(raw));
	}

	@Test
	void logoutIsIdempotentWhenCalledTwice() {
		accounts.save(
				new UserAccount("logout-twice@jugueria.pe", passwordEncoder.encode(PASSWORD), Role.CASHIER, Instant.now()));
		var raw = cookieValue(login("logout-twice@jugueria.pe", PASSWORD));

		assertThat(logout(raw)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(logout(raw)).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void logoutIsANoOpWithoutACookie() {
		var result = mvc.post().uri(LOGOUT).header("X-Requested-With", "XMLHttpRequest").exchange();

		assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void logoutRejectsWithoutTheRequestedWithHeader() {
		var result = mvc.post().uri(LOGOUT).cookie(new Cookie(RefreshTokenCookie.NAME, "whatever")).exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.malformed-request");
	}

	private MvcTestResult login(String email, String password) {
		return mvc.post()
			.uri(LOGIN)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
			.exchange();
	}

	private MvcTestResult refresh(String rawToken) {
		return mvc.post()
			.uri(REFRESH)
			.header("X-Requested-With", "XMLHttpRequest")
			.cookie(new Cookie(RefreshTokenCookie.NAME, rawToken))
			.exchange();
	}

	private MvcTestResult logout(String rawToken) {
		return mvc.post()
			.uri(LOGOUT)
			.header("X-Requested-With", "XMLHttpRequest")
			.cookie(new Cookie(RefreshTokenCookie.NAME, rawToken))
			.exchange();
	}

	private String seedToken(UUID userId, Instant issuedAt, Instant expiresAt) {
		var raw = RefreshTokens.newRawToken();
		refreshTokens.save(new RefreshToken(UUID.randomUUID(), userId, RefreshTokens.hash(raw), issuedAt, expiresAt));
		return raw;
	}

	private static String cookieValue(MvcTestResult result) {
		return result.getResponse().getCookie(RefreshTokenCookie.NAME).getValue();
	}

	private static void assertRefreshCookieIssued(MvcTestResult result) {
		assertThat(result).headers()
			.hasHeaderSatisfying(HttpHeaders.SET_COOKIE,
					values -> assertThat(values).singleElement()
						.asString()
						.contains(RefreshTokenCookie.NAME + "=")
						.contains("HttpOnly")
						.contains("Secure")
						.contains("SameSite=Strict")
						.contains("Path=/"));
	}

	private static void assertInvalidRefreshToken(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.invalid-refresh-token");
	}

	private static void assertInvalidCredentials(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.invalid-credentials");
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
	}

}
