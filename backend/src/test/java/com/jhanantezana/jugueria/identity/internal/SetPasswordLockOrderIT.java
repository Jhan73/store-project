package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Role;

// The staff delete takes the account row and then its tokens, so setting a password must take them in that order too:
// consuming the token first would leave the two waiting on each other until the database aborts one.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class SetPasswordLockOrderIT {

	@Autowired
	SetPasswordService setPasswordService;

	@Autowired
	SetPasswordTokenService tokenService;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	RefreshTokenRepository refreshTokens;

	@Autowired
	SetPasswordTokenRepository setPasswordTokens;

	@Autowired
	PlatformTransactionManager transactions;

	@Autowired
	JdbcClient jdbc;

	final ExecutorService executor = Executors.newFixedThreadPool(2);

	@AfterEach
	void cleanUp() {
		executor.shutdownNow();
		refreshTokens.deleteAll();
		setPasswordTokens.deleteAll();
		accounts.deleteAll();
	}

	@Test
	void settingAPasswordWaitsForTheAccountRowBeforeItTouchesTheToken() throws Exception {
		var account = accounts.save(new UserAccount("maria@jugueria.pe", "hash", Role.CASHIER, Instant.now(), true));
		var raw = tokenService.issue(account.getId()).rawToken();
		var locked = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var holding = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
			accounts.findForUpdateById(account.getId());
			locked.countDown();
			waitFor(release);
		}));
		assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

		var setting = executor.submit(() -> setPasswordService.setPassword(raw, "a-long-enough-password"));
		await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(5)).until(() -> !setting.isDone());

		assertThatCode(() -> jdbc.sql("select id from identity.set_password_token where user_id = ? for update nowait")
			.param(account.getId())
			.query(java.util.UUID.class)
			.list()).doesNotThrowAnyException();

		release.countDown();
		holding.get(10, TimeUnit.SECONDS);
		setting.get(10, TimeUnit.SECONDS);
	}

	private static void waitFor(CountDownLatch latch) {
		try {
			assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

}
