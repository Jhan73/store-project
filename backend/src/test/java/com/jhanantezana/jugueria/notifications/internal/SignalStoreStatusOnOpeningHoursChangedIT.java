package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
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
import com.jhanantezana.jugueria.store.OpeningHourSnapshot;
import com.jhanantezana.jugueria.store.OpeningHoursChanged;

// identity/shared are force-included: see SignalStoreStatusOnStoreSettingsChangedIT for why.
// No STOMP broker involved: just proves the listener signals /topic/store-status after its event commits.
@ApplicationModuleTest(extraIncludes = { "identity", "shared" })
@TestPropertySource(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class SignalStoreStatusOnOpeningHoursChangedIT {

	static final Instant NOW = Instant.parse("2026-09-27T09:00:00Z");

	@Autowired
	JdbcConnectionDetails connectionDetails;

	Connection listenProbe;

	// Another test in this class can leave a stray, unconsumed notification behind; every assertion
	// filters by its own settingsId.
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

		scenario.publish(anOpeningHoursChanged(settingsId))
			.andWaitForStateChange(() -> countMatching(marker), count -> count >= 1)
			.andVerify(count -> assertThat(matching(marker)).singleElement()
				.satisfies(n -> assertThat(n).contains("\"type\":\"OPENING_HOURS_CHANGED\"")));
	}

	@Test
	void deliveringTheSameEventTwiceStillSignalsBothTimesWithoutFailing(Scenario scenario) throws SQLException {
		listen();
		var settingsId = UUID.randomUUID();
		var marker = settingsId.toString();
		var event = anOpeningHoursChanged(settingsId);

		scenario.publish(event).andWaitForStateChange(() -> countMatching(marker), count -> count >= 1).andVerify(count -> {
		});
		scenario.publish(event)
			.andWaitForStateChange(() -> countMatching(marker), count -> count >= 2)
			.andVerify(count -> assertThat(matching(marker)).hasSizeGreaterThanOrEqualTo(2));
	}

	private OpeningHoursChanged anOpeningHoursChanged(UUID settingsId) {
		var before = List.of(new OpeningHourSnapshot(DayOfWeek.MONDAY, false, LocalTime.of(8, 0), LocalTime.of(20, 0)));
		var after = List.of(new OpeningHourSnapshot(DayOfWeek.MONDAY, true, null, null));
		return new OpeningHoursChanged(settingsId, before, after, null, null, NOW);
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
