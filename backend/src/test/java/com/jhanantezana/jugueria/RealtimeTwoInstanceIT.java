package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.ServerPortInfoApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.jhanantezana.jugueria.identity.internal.security.AccessTokenIssuer;
import com.jhanantezana.jugueria.shared.Role;

/**
 * WP M1-B5's own acceptance test: a change committed on instance A must reach a subscriber connected to
 * instance B within 5 seconds (NFR-04), across the LISTEN/NOTIFY bridge, not the in-memory broker alone.
 */
class RealtimeTwoInstanceIT {

	static ConfigurableApplicationContext contextA;

	static ConfigurableApplicationContext contextB;

	static int portA;

	static int portB;

	// Marks when B's own dedicated LISTEN connection could have opened, to find its PID unambiguously.
	static Instant beforeBStarted;

	static final String SETTINGS_JSON = """
			{
			  "timeZone": "America/Lima",
			  "currency": "PEN",
			  "basePrepMinutes": 10,
			  "queueMinutesPerOrder": 2,
			  "busyModeMinutes": 15,
			  "boardWarningMinutes": 5,
			  "boardLateMinutes": 10,
			  "registerDifferenceThreshold": { "amount": "20.00", "currency": "PEN" },
			  "exceptionThreshold": 3,
			  "onlineCapacityLimit": 20
			}
			""";

	private record Connection(StompSession session, CompletableFuture<Throwable> errors) {
	}

	@BeforeAll
	static void startBothInstances() throws SQLException {
		contextA = build().run();
		portA = contextA.getEnvironment().getProperty("local.server.port", Integer.class);

		beforeBStarted = databaseNow();
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

	@Test
	void aChangeCommittedOnInstanceAReachesASubscriberOnInstanceBWithinFiveSeconds() throws Exception {
		var connection = connect(portB, null).get(5, TimeUnit.SECONDS);
		try {
			var received = new CompletableFuture<String>();
			connection.session().subscribe("/topic/store-status", stringHandler(received));

			updateStoreSettingsOnA();

			var payload = received.get(5, TimeUnit.SECONDS);
			assertThat(payload).contains("\"type\":\"STORE_SETTINGS_CHANGED\"").contains("settingsId");
		}
		finally {
			disconnectQuietly(connection.session());
		}
	}

	@Test
	void rejectsConnectWithAnInvalidToken() {
		var headers = new StompHeaders();
		headers.add(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token");

		var connectFuture = connect(portB, headers);

		assertThatThrownBy(() -> connectFuture.get(5, TimeUnit.SECONDS))
			.isInstanceOfAny(ExecutionException.class, TimeoutException.class);
	}

	@Test
	void rejectsSubscribingToADestinationOutsideThisWorkPackage() throws Exception {
		var connection = connect(portB, null).get(5, TimeUnit.SECONDS);
		try {
			connection.session().subscribe("/topic/board", stringHandler(new CompletableFuture<>()));

			assertThat(connection.errors().get(5, TimeUnit.SECONDS)).isNotNull();
		}
		finally {
			disconnectQuietly(connection.session());
		}
	}

	@Test
	void rejectsAClientSendToABrokerDestination() throws Exception {
		var connection = connect(portB, null).get(5, TimeUnit.SECONDS);
		try {
			connection.session().send("/topic/catalog", "not allowed");

			assertThat(connection.errors().get(5, TimeUnit.SECONDS)).isNotNull();
		}
		finally {
			disconnectQuietly(connection.session());
		}
	}

	@Test
	void theListenerReconnectsAfterItsBackendIsTerminatedAndStillDelivers() throws Exception {
		terminateInstanceBsListenerBackend();

		var connection = connect(portB, null).get(5, TimeUnit.SECONDS);
		try {
			var received = new CompletableFuture<String>();
			connection.session().subscribe("/topic/store-status", stringHandler(received));

			updateStoreSettingsOnA();

			// Generous budget: reconnect uses exponential backoff (0.5s to 30s) before the LISTEN resumes.
			var payload = received.get(35, TimeUnit.SECONDS);
			assertThat(payload).contains("\"type\":\"STORE_SETTINGS_CHANGED\"");
		}
		finally {
			disconnectQuietly(connection.session());
		}
	}

	private static void terminateInstanceBsListenerBackend() throws SQLException {
		try (var connection = DriverManager.getConnection(TestcontainersConfiguration.POSTGRES.getJdbcUrl(), "app",
				"app"); var statement = connection.createStatement()) {
			var pid = -1;
			try (var rs = statement.executeQuery(
					"select pid from pg_stat_activity where query = 'LISTEN app_events' and backend_start >= '"
							+ beforeBStarted + "' order by backend_start asc limit 1")) {
				if (rs.next()) {
					pid = rs.getInt("pid");
				}
			}
			assertThat(pid).isPositive();
			statement.execute("select pg_terminate_backend(" + pid + ")");
		}
	}

	private static Instant databaseNow() throws SQLException {
		try (var connection = DriverManager.getConnection(TestcontainersConfiguration.POSTGRES.getJdbcUrl(), "app",
				"app");
				var statement = connection.createStatement();
				var rs = statement.executeQuery("select now()")) {
			rs.next();
			return rs.getTimestamp(1).toInstant();
		}
	}

	private static void updateStoreSettingsOnA() {
		var token = contextA.getBean(AccessTokenIssuer.class).issue(UUID.randomUUID(), Role.ADMIN);
		var restClient = RestClient.builder().baseUrl("http://localhost:" + portA).build();
		var etag = restClient.get()
			.uri("/api/v1/admin/settings")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.retrieve()
			.toEntity(String.class)
			.getHeaders()
			.getETag();
		restClient.put()
			.uri("/api/v1/admin/settings")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.header(HttpHeaders.IF_MATCH, etag)
			.contentType(MediaType.APPLICATION_JSON)
			.body(SETTINGS_JSON)
			.retrieve()
			.toBodilessEntity();
	}

	private static CompletableFuture<Connection> connect(int port, StompHeaders connectHeaders) {
		var stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(new StringMessageConverter());
		var connected = new CompletableFuture<StompSession>();
		var errors = new CompletableFuture<Throwable>();
		var handler = new StompSessionHandlerAdapter() {

			@Override
			public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
				connected.complete(session);
			}

			@Override
			public void handleException(StompSession session, StompCommand command, StompHeaders headers,
					byte[] payload, Throwable exception) {
				failConnectIfPending(exception);
				errors.complete(exception);
			}

			@Override
			public void handleTransportError(StompSession session, Throwable exception) {
				failConnectIfPending(exception);
				errors.complete(exception);
			}

			private void failConnectIfPending(Throwable exception) {
				if (!connected.isDone()) {
					connected.completeExceptionally(exception);
				}
			}

		};
		stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
				connectHeaders != null ? connectHeaders : new StompHeaders(), handler);
		return connected.thenApply(session -> new Connection(session, errors));
	}

	private static StompFrameHandler stringHandler(CompletableFuture<String> received) {
		return new StompFrameHandler() {

			@Override
			public Type getPayloadType(StompHeaders headers) {
				return String.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				received.complete((String) payload);
			}

		};
	}

	private static void disconnectQuietly(StompSession session) {
		try {
			session.disconnect();
		}
		catch (Exception ignored) {
			// The server may have already closed the connection after sending the ERROR frame.
		}
	}

	private static SpringApplicationBuilder build() {
		return new SpringApplicationBuilder(BackendApplication.class)
			.initializers(new ServerPortInfoApplicationContextInitializer())
			.properties("server.port=0", "spring.datasource.url=" + TestcontainersConfiguration.POSTGRES.getJdbcUrl(),
					"spring.datasource.username=app", "spring.datasource.password=app",
					"spring.flyway.user=migrator", "spring.flyway.password=migrator",
					"jugueria.identity.jwt.ephemeral-key-allowed=true",
					"jugueria.identity.allowed-origins=http://localhost:4200");
	}

}
