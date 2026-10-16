package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.ServerPortInfoApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import com.jhanantezana.jugueria.identity.internal.security.AccessTokenIssuer;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.probe.IdempotentProbeController;
import com.jhanantezana.probe.IdempotentProbeService;

// The same command and key sent to two separate application instances over one database must run once.
class IdempotencyTwoInstanceIT {

	static ConfigurableApplicationContext contextA;

	static ConfigurableApplicationContext contextB;

	static int portA;

	static int portB;

	@BeforeAll
	static void startBothInstances() {
		contextA = build().run();
		portA = contextA.getEnvironment().getProperty("local.server.port", Integer.class);
		contextB = build().run();
		portB = contextB.getEnvironment().getProperty("local.server.port", Integer.class);
	}

	@AfterAll
	static void stopBothInstances() {
		if (contextB != null) {
			contextB.close();
		}
		if (contextA != null) {
			contextA.close();
		}
	}

	@AfterEach
	void cleanUp() throws SQLException {
		IdempotentProbeService.afterRegister = () -> {
		};
		try (var connection = DriverManager.getConnection(TestcontainersConfiguration.POSTGRES.getJdbcUrl(), "app",
				"app"); var statement = connection.createStatement()) {
			statement.execute("delete from test_probe.probe_result");
			statement.execute("delete from shared.idempotency_key");
		}
	}

	@Test
	void aDuplicateSentToTheOtherInstanceWhileTheFirstIsRunningWaitsAndReplaysItsResponse() throws Exception {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		var firstIsRunning = new CountDownLatch(1);
		var releaseFirst = new CountDownLatch(1);
		IdempotentProbeService.afterRegister = () -> {
			firstIsRunning.countDown();
			awaitQuietly(releaseFirst);
		};

		var onA = CompletableFuture.supplyAsync(() -> post(contextA, portA, actor, key, "mango"));
		assertThat(firstIsRunning.await(10, TimeUnit.SECONDS)).isTrue();
		var onB = CompletableFuture.supplyAsync(() -> post(contextB, portB, actor, key, "mango"));
		awaitABackendWaitingOnTheKey();
		releaseFirst.countDown();

		var first = onA.get(10, TimeUnit.SECONDS);
		var duplicate = onB.get(10, TimeUnit.SECONDS);
		assertThat(first.getStatusCode().value()).isEqualTo(201);
		assertThat(first.getHeaders().containsHeader("Idempotent-Replayed")).isFalse();
		assertThat(duplicate.getStatusCode().value()).isEqualTo(201);
		assertThat(duplicate.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
		assertThat(duplicate.getBody()).isEqualTo(first.getBody());
		assertThat(results()).isEqualTo(1);
	}

	@Test
	void theSameKeyFiredAtBothInstancesAtTheSameMomentCreatesOneResultEachTime() throws Exception {
		for (var round = 0; round < 10; round++) {
			var actor = UUID.randomUUID();
			var key = UUID.randomUUID();
			var start = new CyclicBarrier(2);

			var onA = CompletableFuture.supplyAsync(() -> {
				awaitQuietly(start);
				return post(contextA, portA, actor, key, "round-" + key);
			});
			var onB = CompletableFuture.supplyAsync(() -> {
				awaitQuietly(start);
				return post(contextB, portB, actor, key, "round-" + key);
			});

			var a = onA.get(10, TimeUnit.SECONDS);
			var b = onB.get(10, TimeUnit.SECONDS);

			assertThat(a.getStatusCode().value()).isEqualTo(201);
			assertThat(b.getStatusCode().value()).isEqualTo(201);
			assertThat(b.getBody()).isEqualTo(a.getBody());
			assertThat(a.getHeaders().containsHeader("Idempotent-Replayed"))
				.isNotEqualTo(b.getHeaders().containsHeader("Idempotent-Replayed"));
			assertThat(resultsNamed("round-" + key)).isEqualTo(1);
		}
	}

	private static ResponseEntity<String> post(ConfigurableApplicationContext context, int port, UUID actor, UUID key,
			String name) {
		var token = context.getBean(AccessTokenIssuer.class).issue(actor, Role.ADMIN);
		return RestClient.builder()
			.baseUrl("http://localhost:" + port)
			.build()
			.post()
			.uri(IdempotentProbeController.PATH)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\":\"" + name + "\"}")
			.retrieve()
			.toEntity(String.class);
	}

	private static long results() throws SQLException {
		return count("select count(*) from test_probe.probe_result");
	}

	private static long resultsNamed(String name) throws SQLException {
		return count("select count(*) from test_probe.probe_result where name = '" + name + "'");
	}

	private static long count(String sql) throws SQLException {
		try (var connection = DriverManager.getConnection(TestcontainersConfiguration.POSTGRES.getJdbcUrl(), "app",
				"app"); var statement = connection.createStatement(); var rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}

	// Polls PostgreSQL itself until the duplicate's insert is blocked behind the first transaction's key row.
	private static void awaitABackendWaitingOnTheKey() throws Exception {
		var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			if (count("""
					select count(*) from pg_stat_activity
					where wait_event_type = 'Lock' and query like 'INSERT INTO shared.idempotency_key%'
					""") > 0) {
				return;
			}
			Thread.sleep(10);
		}
		throw new AssertionError("The duplicate request never blocked on the first transaction's key");
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void awaitQuietly(CyclicBarrier barrier) {
		try {
			barrier.await(10, TimeUnit.SECONDS);
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static SpringApplicationBuilder build() {
		return new SpringApplicationBuilder(BackendApplication.class, IdempotentProbeController.class,
				IdempotentProbeService.class)
			.initializers(new ServerPortInfoApplicationContextInitializer())
			.properties("server.port=0", "spring.datasource.url=" + TestcontainersConfiguration.POSTGRES.getJdbcUrl(),
					"spring.datasource.username=app", "spring.datasource.password=app", "spring.flyway.user=migrator",
					"spring.flyway.password=migrator", "jugueria.identity.jwt.ephemeral-key-allowed=true",
					"jugueria.web.allowed-origins=http://localhost:4200");
	}

}
