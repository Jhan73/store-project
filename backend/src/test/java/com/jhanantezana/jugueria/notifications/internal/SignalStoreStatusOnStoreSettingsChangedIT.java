package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.TestPropertySource;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.jugueria.store.StoreSettingsChanged;
import com.jhanantezana.jugueria.store.StoreSettingsSnapshot;

// identity is force-included: StompAuthChannelInterceptor needs its JwtDecoder/JwtAuthenticationConverter
// beans, a dependency Modulith's static analysis can't see since those are framework types, not identity's
// own; shared is force-included because identity's own beans need its Clock.
// No STOMP broker involved: just proves the listener signals /topic/store-status after its event commits.
@ApplicationModuleTest(extraIncludes = { "identity", "shared" })
@TestPropertySource(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import({ TestcontainersConfiguration.class, CurrentActorProbe.class })
class SignalStoreStatusOnStoreSettingsChangedIT {

	static final Instant NOW = Instant.parse("2026-09-27T09:00:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	@Autowired
	JdbcConnectionDetails connectionDetails;

	@Autowired
	CurrentActorProbe probe;

	Connection listenProbe;

	// Other tests in this class can leave a stray, unconsumed notification behind (their own async
	// listener fires after their own probe already closed); every assertion filters by its own settingsId.
	final List<String> received = new ArrayList<>();

	@AfterEach
	void closeProbe() throws SQLException {
		if (listenProbe != null) {
			listenProbe.close();
		}
	}

	@Test
	void deliversTheSignalAfterTheEventsTransactionCommits(Scenario scenario) throws SQLException {
		listen();
		var settingsId = UUID.randomUUID();
		var marker = settingsId.toString();

		scenario.publish(aStoreSettingsChanged(settingsId))
			.andWaitForStateChange(() -> countMatching(marker), count -> count >= 1)
			.andVerify(count -> assertThat(matching(marker)).singleElement()
				.satisfies(n -> assertThat(n).contains("\"type\":\"STORE_SETTINGS_CHANGED\"")));
	}

	@Test
	void deliveringTheSameEventTwiceStillSignalsBothTimesWithoutFailing(Scenario scenario) throws SQLException {
		listen();
		var settingsId = UUID.randomUUID();
		var marker = settingsId.toString();
		var event = aStoreSettingsChanged(settingsId);

		scenario.publish(event).andWaitForStateChange(() -> countMatching(marker), count -> count >= 1).andVerify(count -> {
		});
		scenario.publish(event)
			.andWaitForStateChange(() -> countMatching(marker), count -> count >= 2)
			.andVerify(count -> assertThat(matching(marker)).hasSizeGreaterThanOrEqualTo(2));
	}

	@Test
	void theListenerSeesCurrentActorAsSystemRatherThanTheOriginalActor(Scenario scenario) throws Exception {
		listen();
		var settingsId = UUID.randomUUID();
		var marker = settingsId.toString();
		var event = new StoreSettingsChanged(settingsId, snapshot(10), snapshot(12), UUID.randomUUID(), Role.ADMIN,
				NOW);

		// Drains this test's own notification too, so it never leaks into a later test's fresh LISTEN.
		scenario.publish(event).andWaitForStateChange(() -> countMatching(marker), count -> count >= 1).andVerify(count -> {
		});

		assertThat(probe.awaitSawSystemActor()).isTrue();
	}

	private StoreSettingsChanged aStoreSettingsChanged(UUID settingsId) {
		return new StoreSettingsChanged(settingsId, snapshot(10), snapshot(12), null, null, NOW);
	}

	private static StoreSettingsSnapshot snapshot(int basePrepMinutes) {
		return new StoreSettingsSnapshot("America/Lima", PEN, basePrepMinutes, 2, 15, 5, 10, Money.of("20.00", PEN), 3,
				20);
	}

	private void listen() throws SQLException {
		listenProbe = DriverManager.getConnection(connectionDetails.getJdbcUrl(), connectionDetails.getUsername(),
				connectionDetails.getPassword());
		try (var statement = listenProbe.createStatement()) {
			statement.execute("LISTEN app_events");
		}
	}

	private List<String> matching(String marker) {
		return received.stream().filter(n -> n.contains(marker)).toList();
	}

	// One getNotifications() call can return several pending notifications at once; accumulate across polls.
	private long countMatching(String marker) {
		try {
			var notifications = listenProbe.unwrap(PGConnection.class).getNotifications(200);
			if (notifications != null) {
				for (var notification : notifications) {
					received.add(notification.getParameter());
				}
			}
			return matching(marker).size();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Failed to poll for a notification", e);
		}
	}

}
