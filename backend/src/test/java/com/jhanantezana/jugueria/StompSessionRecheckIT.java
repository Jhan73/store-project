package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.ServerPortInfoApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.jhanantezana.jugueria.identity.internal.UserAccount;
import com.jhanantezana.jugueria.identity.internal.UserAccountRepository;
import com.jhanantezana.jugueria.identity.internal.security.AccessTokenIssuer;
import com.jhanantezana.jugueria.shared.Role;

// The deactivation signal is not involved: the account is deactivated straight in the database, so the only thing that
// can close the session is the lookup made when the token the session connected with expires.
class StompSessionRecheckIT {

	static ConfigurableApplicationContext context;

	static int port;

	@BeforeAll
	static void start() {
		context = new SpringApplicationBuilder(BackendApplication.class)
			.initializers(new ServerPortInfoApplicationContextInitializer())
			.properties("server.port=0", "spring.datasource.url=" + TestcontainersConfiguration.POSTGRES.getJdbcUrl(),
					"spring.datasource.username=app", "spring.datasource.password=app", "spring.flyway.user=migrator",
					"spring.flyway.password=migrator", "jugueria.identity.jwt.ephemeral-key-allowed=true",
					"jugueria.notifications.session-recheck.sweep-interval=500ms",
					"jugueria.notifications.session-recheck.account-recheck-interval=500ms",
					"jugueria.web.allowed-origins=http://localhost:4200")
			// A command-line argument: application.properties would otherwise win over the builder's defaults.
			.run("--jugueria.identity.jwt.access-token-ttl=2s");
		port = context.getEnvironment().getProperty("local.server.port", Integer.class);
	}

	@AfterAll
	static void stop() throws SQLException {
		if (context != null) {
			context.close();
		}
		try (var connection = DriverManager.getConnection(TestcontainersConfiguration.POSTGRES.getJdbcUrl(), "app", "app");
				var statement = connection.createStatement()) {
			statement.execute("delete from identity.user_account where email like 'recheck-it-%'");
		}
	}

	@Test
	void closesTheSessionOfAnAccountDeactivatedWithoutASignalOnceItsTokenExpiredAndKeepsAnActiveOne() throws Exception {
		var leaving = account();
		var staying = account();
		var leavingClosed = new CompletableFuture<Throwable>();
		var leavingSession = connect(leaving, leavingClosed);
		var stayingClosed = new CompletableFuture<Throwable>();
		var stayingSession = connect(staying, stayingClosed);
		try {
			deactivate(leaving);

			assertThat(leavingClosed.get(15, TimeUnit.SECONDS)).isNotNull();
			await().during(Duration.ofSeconds(4)).atMost(Duration.ofSeconds(8)).until(stayingSession::isConnected);
			assertThat(stayingClosed).isNotDone();
		}
		finally {
			disconnectQuietly(leavingSession);
			disconnectQuietly(stayingSession);
		}
	}

	private static UUID account() {
		var accounts = context.getBean(UserAccountRepository.class);
		return accounts.save(new UserAccount("recheck-it-" + UUID.randomUUID() + "@jugueria.pe", "hash", Role.CASHIER,
				Instant.now()))
			.getId();
	}

	private static void deactivate(UUID id) throws SQLException {
		try (var connection = DriverManager.getConnection(TestcontainersConfiguration.POSTGRES.getJdbcUrl(), "app", "app");
				var statement = connection.createStatement()) {
			statement.execute("update identity.user_account set active = false where id = '" + id + "'");
		}
	}

	private static StompSession connect(UUID user, CompletableFuture<Throwable> closed) throws Exception {
		var client = new WebSocketStompClient(new StandardWebSocketClient());
		var headers = new StompHeaders();
		headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + context.getBean(AccessTokenIssuer.class).issue(user, Role.CASHIER));
		var connected = new CompletableFuture<StompSession>();
		var handler = new StompSessionHandlerAdapter() {

			@Override
			public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
				connected.complete(session);
			}

			@Override
			public void handleTransportError(StompSession session, Throwable exception) {
				connected.completeExceptionally(exception);
				closed.complete(exception);
			}

		};
		client.connectAsync("ws://localhost:" + port + "/ws", new org.springframework.web.socket.WebSocketHttpHeaders(),
				headers, handler);
		return connected.get(5, TimeUnit.SECONDS);
	}

	private static void disconnectQuietly(StompSession session) {
		try {
			session.disconnect();
		}
		catch (Exception ignored) {
			// The server may already have closed it.
		}
	}

}
