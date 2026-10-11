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
import com.jhanantezana.jugueria.preparation.BoardChanged;

// identity and shared are force-included for the same reason as the catalog signal tests.
@ApplicationModuleTest(extraIncludes = { "identity", "shared" })
@TestPropertySource(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class SignalBoardOnBoardChangedIT {

	static final Instant NOW = Instant.parse("2026-10-10T09:00:00Z");

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
	void signalsTheBoardTopicWithTheOrderAndLineIds(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var order = UUID.randomUUID();
		var line = UUID.randomUUID();

		scenario.publish(new BoardChanged("LINE_READY", order, line, NOW))
			.andWaitForStateChange(() -> probe.countMatching(order.toString()), count -> count >= 1)
			.andVerify(count -> assertThat(probe.matching(order.toString())).singleElement().satisfies(n -> {
				assertThat(n).contains("\"topic\":\"board\"");
				assertThat(n).contains("\"type\":\"LINE_READY\"");
				assertThat(n).contains("\"orderId\":\"" + order + "\"");
				assertThat(n).contains("\"lineId\":\"" + line + "\"");
			}));
	}

	@Test
	void leavesTheLineOutWhenTheChangeIsAboutTheWholeOrder(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var order = UUID.randomUUID();

		scenario.publish(new BoardChanged("ORDER_STARTED", order, null, NOW))
			.andWaitForStateChange(() -> probe.countMatching(order.toString()), count -> count >= 1)
			.andVerify(count -> assertThat(probe.matching(order.toString())).singleElement().satisfies(n -> {
				assertThat(n).contains("\"type\":\"ORDER_STARTED\"");
				assertThat(n).doesNotContain("lineId");
			}));
	}

	@Test
	void deliveringTheSameChangeTwiceSignalsBothTimesWithoutFailing(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var order = UUID.randomUUID();
		var event = new BoardChanged("ORDER_READY", order, null, NOW);

		scenario.publish(event).andWaitForStateChange(() -> probe.countMatching(order.toString()), count -> count >= 1)
			.andVerify(count -> {
			});
		scenario.publish(event)
			.andWaitForStateChange(() -> probe.countMatching(order.toString()), count -> count >= 2)
			.andVerify(count -> assertThat(probe.matching(order.toString())).hasSizeGreaterThanOrEqualTo(2));
	}

}
