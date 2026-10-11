package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.TestPropertySource;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.instore.TableOccupancyChanged;

// identity and shared are force-included for the same reason as the board signal tests.
@ApplicationModuleTest(extraIncludes = { "identity", "shared" })
@TestPropertySource(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class SignalTablesOnTableOccupancyChangedIT {

	static final Instant NOW = Instant.parse("2026-10-11T09:00:00Z");

	@Autowired
	JdbcConnectionDetails connectionDetails;

	AppEventsProbe probe;

	@AfterEach
	void closeProbe() throws SQLException {
		if (probe != null) {
			probe.close();
		}
	}

	@Test
	void signalsTheTablesTopicWithTheTableId(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var table = UUID.randomUUID();

		scenario.publish(new TableOccupancyChanged(table, NOW))
			.andWaitForStateChange(() -> probe.countMatching(table.toString()), count -> count >= 1)
			.andVerify(count -> assertThat(probe.matching(table.toString())).singleElement().satisfies(n -> {
				assertThat(n).contains("\"topic\":\"tables\"");
				assertThat(n).contains("\"type\":\"TABLE_OCCUPANCY_CHANGED\"");
				assertThat(n).contains("\"tableId\":\"" + table + "\"");
			}));
	}

	@Test
	void deliveringTheSameChangeTwiceSignalsBothTimesWithoutFailing(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var table = UUID.randomUUID();
		var event = new TableOccupancyChanged(table, NOW);

		scenario.publish(event).andWaitForStateChange(() -> probe.countMatching(table.toString()), count -> count >= 1)
			.andVerify(count -> {
			});
		scenario.publish(event)
			.andWaitForStateChange(() -> probe.countMatching(table.toString()), count -> count >= 2)
			.andVerify(count -> assertThat(probe.matching(table.toString())).hasSizeGreaterThanOrEqualTo(2));
	}

}
