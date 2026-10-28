package com.jhanantezana.jugueria.instore.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.instore.PaymentMethod;
import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.instore.internal.WalletProviderService;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;
import com.jhanantezana.testsupport.InstoreTables;

// A payment that checked the provider is active holds a share lock on it until it commits, so a deactivation cannot
// commit between that check and the payment row.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WalletProviderDeactivationRaceIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	WalletProviderService providers;

	@Autowired
	PlatformTransactionManager transactions;

	final ExecutorService executor = Executors.newFixedThreadPool(2);

	@AfterEach
	void cleanUp() {
		executor.shutdownNow();
		InstoreTables.clean(jdbc);
	}

	@Test
	void aDeactivationWaitsForAPaymentThatAlreadyCheckedTheProvider() throws Exception {
		var provider = InstoreTables.aWalletProvider(jdbc, "Wallet-" + UUID.randomUUID(), true);
		var checked = new CountDownLatch(1);
		var commit = new CountDownLatch(1);
		var paying = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
			providers.requireFor(PaymentMethod.WALLET, provider);
			checked.countDown();
			waitFor(commit);
		}));
		assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();

		var deactivating = executor.submit(() -> mvc.post()
			.uri("/api/v1/admin/wallet-providers/" + provider + "/deactivate")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.header(HttpHeaders.IF_MATCH, "\"0\"")
			.exchange());
		await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(5)).until(() -> !deactivating.isDone());
		commit.countDown();

		paying.get(10, TimeUnit.SECONDS);
		assertThat(deactivating.get(10, TimeUnit.SECONDS)).hasStatusOk();
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
