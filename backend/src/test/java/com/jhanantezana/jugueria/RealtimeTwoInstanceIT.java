package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.sql.DriverManager;
import java.sql.SQLException;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.jhanantezana.jugueria.identity.internal.security.AccessTokenIssuer;
import com.jhanantezana.jugueria.shared.Role;

import tools.jackson.databind.json.JsonMapper;

// A change committed on instance A must reach a subscriber connected to instance B within 5 seconds,
// across the LISTEN/NOTIFY bridge, not the in-memory broker alone.
class RealtimeTwoInstanceIT {

	static ConfigurableApplicationContext contextA;

	static ConfigurableApplicationContext contextB;

	static int portA;

	static int portB;

	static final String LISTENER_APPLICATION_NAME_B = "jugueria-listen-b-it";

	static final JsonMapper JSON = JsonMapper.builder().build();

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

	// session fails exceptionally on a rejected CONNECT. errorFrame carries the STOMP ERROR frame's own
	// text (from handleException); transportClosed is the generic close that can race ahead of it and
	// never carries the frame's message, so a test wanting the actual rejection reason waits on errorFrame.
	private record Connection(CompletableFuture<StompSession> session, CompletableFuture<Throwable> errorFrame,
			CompletableFuture<Throwable> transportClosed) {

		CompletableFuture<Throwable> anyRejection() {
			return errorFrame.applyToEither(transportClosed, throwable -> throwable);
		}

	}

	@BeforeAll
	static void startBothInstances() {
		contextA = build("jugueria-listen-a-it").run();
		portA = contextA.getEnvironment().getProperty("local.server.port", Integer.class);

		contextB = build(LISTENER_APPLICATION_NAME_B).run();
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
		var connection = connect(portB, null);
		var session = connection.session().get(5, TimeUnit.SECONDS);
		try {
			var received = new CompletableFuture<String>();
			session.subscribe("/topic/store-status", stringHandler(received));

			updateStoreSettingsOnA();

			var payload = received.get(5, TimeUnit.SECONDS);
			assertThat(payload).contains("\"type\":\"STORE_SETTINGS_CHANGED\"").contains("settingsId");
		}
		finally {
			disconnectQuietly(session);
		}
	}

	// Bypasses the STOMP client abstraction: DefaultStompSession never surfaces a pre-CONNECTED ERROR
	// frame's own text through handleException, only a generic "Connection closed" transport failure.
	// Reading the raw frame is the only way to assert the server's actual rejection reason.
	@Test
	void rejectsConnectWithAnInvalidTokenViaAnErrorFrame() throws Exception {
		var received = new CompletableFuture<String>();
		var handler = new TextWebSocketHandler() {

			@Override
			protected void handleTextMessage(WebSocketSession session, TextMessage message) {
				received.complete(message.getPayload());
			}

		};
		var client = new StandardWebSocketClient();
		var session = client.execute(handler, "ws://localhost:" + portB + "/ws").get(5, TimeUnit.SECONDS);
		try {
			var connectFrame = "CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer not-a-real-token\n\n\u0000";
			session.sendMessage(new TextMessage(connectFrame));

			var frame = received.get(5, TimeUnit.SECONDS);
			assertThat(frame).startsWith("ERROR").contains("Invalid or expired token");
		}
		finally {
			session.close();
		}
	}

	@Test
	void rejectsSubscribingToADestinationOutsideThisWorkPackage() throws Exception {
		var connection = connect(portB, null);
		var session = connection.session().get(5, TimeUnit.SECONDS);
		try {
			session.subscribe("/topic/board", stringHandler(new CompletableFuture<>()));

			assertThat(connection.anyRejection().get(5, TimeUnit.SECONDS)).isNotNull();
		}
		finally {
			disconnectQuietly(session);
		}
	}

	@Test
	void rejectsAClientSendToABrokerDestination() throws Exception {
		var connection = connect(portB, null);
		var session = connection.session().get(5, TimeUnit.SECONDS);
		try {
			session.send("/topic/catalog", "not allowed");

			assertThat(connection.anyRejection().get(5, TimeUnit.SECONDS)).isNotNull();
		}
		finally {
			disconnectQuietly(session);
		}
	}

	@Test
	void rejectsAClientSendToAnApplicationDestination() throws Exception {
		var connection = connect(portB, null);
		var session = connection.session().get(5, TimeUnit.SECONDS);
		try {
			session.send("/app/whatever", "not allowed");

			assertThat(connection.anyRejection().get(5, TimeUnit.SECONDS)).isNotNull();
		}
		finally {
			disconnectQuietly(session);
		}
	}

	@Test
	void rejectsAClientSendToAnArbitraryDestination() throws Exception {
		var connection = connect(portB, null);
		var session = connection.session().get(5, TimeUnit.SECONDS);
		try {
			session.send("/something/else", "not allowed");

			assertThat(connection.anyRejection().get(5, TimeUnit.SECONDS)).isNotNull();
		}
		finally {
			disconnectQuietly(session);
		}
	}

	@Test
	void theListenerReconnectsAfterItsBackendIsTerminatedAndStillDelivers() throws Exception {
		var oldPid = terminateInstanceBsListenerBackend();
		// PostgreSQL never re-delivers a NOTIFY to a listener that wasn't connected at the moment it was
		// sent; publishing before the new LISTEN is active would lose the signal for good, not just delay
		// it. Waiting for a new pid (distinct from the terminated one) proves the reconnect actually landed.
		awaitListenerBackendPid(LISTENER_APPLICATION_NAME_B, oldPid);

		var connection = connect(portB, null);
		var session = connection.session().get(5, TimeUnit.SECONDS);
		try {
			var received = new CompletableFuture<String>();
			session.subscribe("/topic/store-status", stringHandler(received));

			updateStoreSettingsOnA();

			var payload = received.get(5, TimeUnit.SECONDS);
			assertThat(payload).contains("\"type\":\"STORE_SETTINGS_CHANGED\"");
		}
		finally {
			disconnectQuietly(session);
		}
	}

	@Test
	void anAvailabilityChangeOnInstanceAReachesACatalogSubscriberOnInstanceBWithinFiveSeconds() throws Exception {
		var productId = createProductOnA();
		var connection = connect(portB, null);
		var session = connection.session().get(5, TimeUnit.SECONDS);
		try {
			var received = new CompletableFuture<String>();
			session.subscribe("/topic/catalog", stringHandler(received, "PRODUCT_AVAILABILITY_CHANGED"));

			setAvailabilityOnA(productId, false);

			assertThat(received.get(5, TimeUnit.SECONDS)).contains(productId);
		}
		finally {
			disconnectQuietly(session);
			deleteCatalogRows();
		}
	}

	// B's menu is cached in B's own memory; only the NOTIFY from A can tell B to drop it before the safety expiry.
	@Test
	void anAvailabilityChangeOnInstanceAShowsInInstanceBsCachedMenuWithinFiveSeconds() throws Exception {
		var productId = createProductOnA();
		try {
			awaitMenuOnB(productId, true, 5);
			var etagBefore = menuOnB().getHeaders().getETag();

			setAvailabilityOnA(productId, false);

			var seconds = awaitMenuOnB(productId, false, 5);
			assertThat(seconds).isLessThanOrEqualTo(5.0);
			assertThat(menuOnB().getHeaders().getETag()).isNotEqualTo(etagBefore);
		}
		finally {
			deleteCatalogRows();
		}
	}

	private static String createProductOnA() {
		var client = clientOnA();
		var token = "Bearer " + contextA.getBean(AccessTokenIssuer.class).issue(UUID.randomUUID(), Role.ADMIN);
		var category = client.post()
			.uri("/api/v1/admin/categories")
			.header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"name\":\"Cat-" + UUID.randomUUID() + "\",\"displayOrder\":0}")
			.retrieve()
			.body(String.class);
		var product = client.post()
			.uri("/api/v1/admin/products")
			.header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.body("""
					{ "name": "Mango-%s", "categoryId": "%s", "price": { "amount": "5.00", "currency": "PEN" },
					  "displayOrder": 0, "quickSalePinned": false }
					""".formatted(UUID.randomUUID(), jsonField(category, "id")))
			.retrieve()
			.body(String.class);
		return jsonField(product, "id");
	}

	private static void setAvailabilityOnA(String productId, boolean available) {
		var token = contextA.getBean(AccessTokenIssuer.class).issue(UUID.randomUUID(), Role.SERVER);
		clientOnA().put()
			.uri("/api/v1/catalog/products/" + productId + "/availability")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"available\":" + available + "}")
			.retrieve()
			.toBodilessEntity();
	}

	private static ResponseEntity<String> menuOnB() {
		return RestClient.create("http://localhost:" + portB).get().uri("/api/v1/catalog/menu").retrieve().toEntity(String.class);
	}

	// Polls B's menu until the product shows the given availability; returns how long that took, in seconds.
	// Sleeping between polls is fine here: the wait is on another instance's asynchronous fan-out, not on a clock.
	private static double awaitMenuOnB(String productId, boolean available, int timeoutSeconds)
			throws InterruptedException {
		var start = System.nanoTime();
		var deadline = start + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		while (System.nanoTime() < deadline) {
			var shown = availabilityOnB(productId);
			if (shown != null && shown == available) {
				return (System.nanoTime() - start) / 1_000_000_000.0;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("Instance B's menu never showed product " + productId + " as available=" + available
				+ " within " + timeoutSeconds + "s");
	}

	private static Boolean availabilityOnB(String productId) {
		var menu = JSON.readTree(menuOnB().getBody());
		for (var category : menu.path("categories")) {
			for (var product : category.path("products")) {
				if (productId.equals(product.path("id").asString())) {
					return product.path("available").asBoolean();
				}
			}
		}
		return null;
	}

	private static String jsonField(String json, String field) {
		return JSON.readTree(json).path(field).asString();
	}

	private static RestClient clientOnA() {
		return RestClient.builder().baseUrl("http://localhost:" + portA).build();
	}

	private static void deleteCatalogRows() throws SQLException {
		try (var connection = DriverManager.getConnection(TestcontainersConfiguration.POSTGRES.getJdbcUrl(), "app",
				"app"); var statement = connection.createStatement()) {
			statement.execute("delete from catalog.product");
			statement.execute("delete from catalog.category");
		}
	}

	private static int terminateInstanceBsListenerBackend() throws SQLException {
		var pid = awaitListenerBackendPid(LISTENER_APPLICATION_NAME_B, null);
		try (var connection = DriverManager.getConnection(TestcontainersConfiguration.POSTGRES.getJdbcUrl(), "app",
				"app"); var statement = connection.createStatement()) {
			statement.execute("select pg_terminate_backend(" + pid + ")");
		}
		return pid;
	}

	// The LISTEN connection opens on its own virtual thread after context startup (or after a reconnect),
	// so the row can lag briefly. excludedPid filters out a just-terminated backend that PostgreSQL may
	// not have dropped from pg_stat_activity yet, so a caller waiting for reconnection doesn't see it.
	private static int awaitListenerBackendPid(String applicationName, Integer excludedPid) throws SQLException {
		try (var connection = DriverManager.getConnection(TestcontainersConfiguration.POSTGRES.getJdbcUrl(), "app",
				"app"); var statement = connection.createStatement()) {
			var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (System.nanoTime() < deadline) {
				try (var rs = statement.executeQuery(
						"select pid from pg_stat_activity where application_name = '" + applicationName + "'")) {
					if (rs.next()) {
						var pid = rs.getInt("pid");
						if (excludedPid == null || pid != excludedPid) {
							return pid;
						}
					}
				}
			}
			throw new AssertionError("No pg_stat_activity row for application_name=" + applicationName
					+ (excludedPid != null ? " other than pid=" + excludedPid : "") + " after 10s");
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

	// Returns immediately with independent futures, rather than one future for the whole exchange: a
	// rejected CONNECT can race a transport close, and a caller wanting the real rejection reason needs
	// the ERROR frame's own text specifically, not whichever callback happens to fire first.
	private static Connection connect(int port, StompHeaders connectHeaders) {
		var stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(new StringMessageConverter());
		var connected = new CompletableFuture<StompSession>();
		var errorFrame = new CompletableFuture<Throwable>();
		var transportClosed = new CompletableFuture<Throwable>();
		var handler = new StompSessionHandlerAdapter() {

			@Override
			public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
				connected.complete(session);
			}

			@Override
			public void handleException(StompSession session, StompCommand command, StompHeaders headers,
					byte[] payload, Throwable exception) {
				failConnectIfPending(exception);
				errorFrame.complete(exception);
			}

			@Override
			public void handleTransportError(StompSession session, Throwable exception) {
				failConnectIfPending(exception);
				transportClosed.complete(exception);
			}

			private void failConnectIfPending(Throwable exception) {
				if (!connected.isDone()) {
					connected.completeExceptionally(exception);
				}
			}

		};
		stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
				connectHeaders != null ? connectHeaders : new StompHeaders(), handler);
		return new Connection(connected, errorFrame, transportClosed);
	}

	// Completes on the first payload containing the marker, ignoring earlier signals that share the topic.
	private static StompFrameHandler stringHandler(CompletableFuture<String> received, String marker) {
		return new StompFrameHandler() {

			@Override
			public Type getPayloadType(StompHeaders headers) {
				return String.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				if (((String) payload).contains(marker)) {
					received.complete((String) payload);
				}
			}

		};
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

	private static SpringApplicationBuilder build(String listenerApplicationName) {
		return new SpringApplicationBuilder(BackendApplication.class)
			.initializers(new ServerPortInfoApplicationContextInitializer())
			.properties("server.port=0", "spring.datasource.url=" + TestcontainersConfiguration.POSTGRES.getJdbcUrl(),
					"spring.datasource.username=app", "spring.datasource.password=app",
					"spring.flyway.user=migrator", "spring.flyway.password=migrator",
					"jugueria.identity.jwt.ephemeral-key-allowed=true",
					"jugueria.web.allowed-origins=http://localhost:4200",
					"jugueria.notifications.listener-application-name=" + listenerApplicationName);
	}

}
