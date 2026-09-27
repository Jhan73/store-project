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
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.jugueria.store.StoreSettingsChanged;
import com.jhanantezana.jugueria.store.StoreSettingsSnapshot;

// No STOMP broker involved: just proves the listener signals /topic/store-status after its event commits.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import({ TestcontainersConfiguration.class, CurrentActorProbe.class })
class SignalStoreStatusOnStoreSettingsChangedIT {

	static final Instant NOW = Instant.parse("2026-09-27T09:00:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Autowired
	JdbcConnectionDetails connectionDetails;

	@Autowired
	CurrentActorProbe probe;

	Connection listenProbe;

	@AfterEach
	void closeProbe() throws SQLException {
		if (listenProbe != null) {
			listenProbe.close();
		}
	}

	@Test
	void deliversTheSignalAfterTheEventsTransactionCommits() throws SQLException {
		listen();
		var settingsId = UUID.randomUUID();

		commit(aStoreSettingsChanged(settingsId));

		var notifications = awaitNotifications(1);
		assertThat(notifications).singleElement()
			.satisfies(n -> assertThat(n).contains("\"type\":\"STORE_SETTINGS_CHANGED\"").contains(settingsId.toString()));
	}

	@Test
	void deliveringTheSameEventTwiceStillSignalsBothTimesWithoutFailing() throws SQLException {
		listen();
		var settingsId = UUID.randomUUID();
		var event = aStoreSettingsChanged(settingsId);

		commit(event);
		commit(event);

		var notifications = awaitNotifications(2);
		assertThat(notifications).hasSizeGreaterThanOrEqualTo(2).allMatch(n -> n.contains(settingsId.toString()));
	}

	@Test
	void theListenerSeesCurrentActorAsSystemRatherThanTheOriginalActor() throws Exception {
		var event = new StoreSettingsChanged(UUID.randomUUID(), snapshot(10), snapshot(12), UUID.randomUUID(),
				Role.ADMIN, NOW);

		commit(event);

		assertThat(probe.awaitSawSystemActor()).isTrue();
	}

	private StoreSettingsChanged aStoreSettingsChanged(UUID settingsId) {
		return new StoreSettingsChanged(settingsId, snapshot(10), snapshot(12), null, null, NOW);
	}

	private static StoreSettingsSnapshot snapshot(int basePrepMinutes) {
		return new StoreSettingsSnapshot("America/Lima", PEN, basePrepMinutes, 2, 15, 5, 10, Money.of("20.00", PEN), 3,
				20);
	}

	private void commit(StoreSettingsChanged event) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> events.publishEvent(event));
	}

	private void listen() throws SQLException {
		listenProbe = DriverManager.getConnection(connectionDetails.getJdbcUrl(), connectionDetails.getUsername(),
				connectionDetails.getPassword());
		try (var statement = listenProbe.createStatement()) {
			statement.execute("LISTEN app_events");
		}
	}

	// One getNotifications() call can return several pending notifications at once; poll until minCount arrive.
	private List<String> awaitNotifications(int minCount) throws SQLException {
		var pg = listenProbe.unwrap(PGConnection.class);
		var received = new ArrayList<String>();
		var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (received.size() < minCount && System.nanoTime() < deadline) {
			var remainingMillis = (int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
			var notifications = pg.getNotifications(remainingMillis);
			if (notifications != null) {
				for (var notification : notifications) {
					received.add(notification.getParameter());
				}
			}
		}
		return received;
	}

}
