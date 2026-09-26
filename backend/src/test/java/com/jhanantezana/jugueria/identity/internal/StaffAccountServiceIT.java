package com.jhanantezana.jugueria.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Role;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class StaffAccountServiceIT {

	@Autowired
	StaffAccountService service;

	@Autowired
	StaffProvisioningService provisioning;

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
	void onlyOneOfADemoteAndADeactivateAgainstTheLastTwoActiveAdminsSucceeds() throws InterruptedException {
		var x = accounts.save(new UserAccount("admin-x@jugueria.pe", "hash", Role.ADMIN, Instant.now()));
		var y = accounts.save(new UserAccount("admin-y@jugueria.pe", "hash", Role.ADMIN, Instant.now()));
		var successes = new AtomicInteger();
		var rejections = new AtomicInteger();
		var ready = new CountDownLatch(2);
		var go = new CountDownLatch(1);
		var pool = Executors.newFixedThreadPool(2);
		try {
			pool.submit(() -> {
				ready.countDown();
				await(go);
				runGuarded(() -> service.changeRole(x.getId(), Role.CASHIER), successes, rejections);
			});
			pool.submit(() -> {
				ready.countDown();
				await(go);
				runGuarded(() -> service.deactivate(y.getId()), successes, rejections);
			});
			ready.await();
			go.countDown();
			pool.shutdown();
			pool.awaitTermination(10, TimeUnit.SECONDS);
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(successes.get()).isEqualTo(1);
		assertThat(rejections.get()).isEqualTo(1);
		assertThat(accounts.countByRoleAndActiveTrue(Role.ADMIN)).isEqualTo(1);
	}

	@Test
	void onlyOneOfTwoConcurrentBootstrapAttemptsCreatesTheFirstAdmin() throws InterruptedException {
		var successes = new AtomicInteger();
		var noOps = new AtomicInteger();
		var ready = new CountDownLatch(2);
		var go = new CountDownLatch(1);
		var pool = Executors.newFixedThreadPool(2);
		try {
			for (var i = 0; i < 2; i++) {
				var email = "owner-" + i + "@jugueria.pe";
				pool.submit(() -> {
					ready.countDown();
					await(go);
					if (provisioning.createFirstAdmin(email).isPresent()) {
						successes.incrementAndGet();
					}
					else {
						noOps.incrementAndGet();
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
		assertThat(noOps.get()).isEqualTo(1);
		assertThat(accounts.countByRoleAndActiveTrue(Role.ADMIN)).isEqualTo(1);
	}

	private static void runGuarded(Runnable action, AtomicInteger successes, AtomicInteger rejections) {
		try {
			action.run();
			successes.incrementAndGet();
		}
		catch (BusinessException e) {
			rejections.incrementAndGet();
		}
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
