package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.TestPropertySource;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.preparation.BoardChanged;

// identity and shared are force-included for the same reason as the other signal tests.
@ApplicationModuleTest(extraIncludes = { "identity", "shared" })
@TestPropertySource(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class SignalDisplayOnBoardChangedIT {

	static final Instant NOW = Instant.parse("2026-10-10T09:00:00Z");

	static final String DISPLAY = "\"topic\":\"display\"";

	@Autowired
	JdbcConnectionDetails connectionDetails;

	AppEventsProbe probe;

	@AfterEach
	void closeProbe() throws SQLException {
		if (probe != null) {
			probe.close();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "ORDER_ADDED", "LINES_ADDED", "ORDER_READY", "ORDER_RECALLED", "ORDER_HANDED_OVER",
			"ORDER_CANCELLED", "ORDER_RELABELLED", "LINE_VOIDED" })
	void signalsTheDisplayTopicWithNoIdsWhenWhatTheScreenShowsMayHaveChanged(String type, Scenario scenario)
			throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var order = UUID.randomUUID();

		scenario.publish(new BoardChanged(type, order, UUID.randomUUID(), NOW))
			.andWaitForStateChange(() -> probe.countMatching(DISPLAY), count -> count >= 1)
			.andVerify(count -> assertThat(probe.matching(DISPLAY)).singleElement().satisfies(n -> {
				assertThat(n).contains("\"type\":\"DISPLAY_CHANGED\"");
				assertThat(n).contains("\"ids\":{}");
				assertThat(n).doesNotContain(order.toString());
			}));
	}

	@ParameterizedTest
	@ValueSource(strings = { "ORDER_STARTED", "LINE_READY", "LINE_FLAGGED", "MARKER_ACKNOWLEDGED" })
	void staysQuietForChangesThePublicScreenCannotShow(String type, Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var quiet = UUID.randomUUID();
		var loud = UUID.randomUUID();

		scenario.publish(new BoardChanged(type, quiet, null, NOW))
			.andWaitForStateChange(() -> probe.countMatching(quiet.toString()), count -> count >= 1)
			.andVerify(count -> {
			});
		scenario.publish(new BoardChanged("ORDER_READY", loud, null, NOW))
			.andWaitForStateChange(() -> probe.countMatching(DISPLAY), count -> count >= 1)
			.andVerify(count -> assertThat(probe.matching(DISPLAY)).hasSize(1));
	}

	@Test
	void deliveringTheSameChangeTwiceSignalsBothTimesWithoutFailing(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var event = new BoardChanged("ORDER_READY", UUID.randomUUID(), null, NOW);

		scenario.publish(event).andWaitForStateChange(() -> probe.countMatching(DISPLAY), count -> count >= 1)
			.andVerify(count -> {
			});
		scenario.publish(event)
			.andWaitForStateChange(() -> probe.countMatching(DISPLAY), count -> count >= 2)
			.andVerify(count -> assertThat(probe.matching(DISPLAY)).hasSizeGreaterThanOrEqualTo(2));
	}

}
