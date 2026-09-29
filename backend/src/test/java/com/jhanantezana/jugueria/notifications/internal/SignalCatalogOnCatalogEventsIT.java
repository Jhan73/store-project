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
import com.jhanantezana.jugueria.catalog.CatalogChangeKind;
import com.jhanantezana.jugueria.catalog.CategoryChanged;
import com.jhanantezana.jugueria.catalog.ModifierGroupChanged;
import com.jhanantezana.jugueria.catalog.StationChanged;

// identity and shared are force-included for the same reason as the store-status signal tests.
@ApplicationModuleTest(extraIncludes = { "identity", "shared" })
@TestPropertySource(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class SignalCatalogOnCatalogEventsIT {

	static final Instant NOW = Instant.parse("2026-09-29T09:00:00Z");

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
	void signalsTheCatalogTopicWhenAStationChanges(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var stationId = UUID.randomUUID();

		scenario.publish(new StationChanged(stationId, CatalogChangeKind.CREATED, null, null, NOW))
			.andWaitForStateChange(() -> probe.countMatching(stationId.toString()), count -> count >= 1)
			.andVerify(count -> assertThat(probe.matching(stationId.toString())).singleElement().satisfies(n -> {
				assertThat(n).contains("\"topic\":\"catalog\"");
				assertThat(n).contains("\"type\":\"STATION_CHANGED\"");
			}));
	}

	@Test
	void signalsTheCatalogTopicWhenACategoryChanges(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var categoryId = UUID.randomUUID();

		scenario.publish(new CategoryChanged(categoryId, CatalogChangeKind.UPDATED, null, null, NOW))
			.andWaitForStateChange(() -> probe.countMatching(categoryId.toString()), count -> count >= 1)
			.andVerify(count -> assertThat(probe.matching(categoryId.toString())).singleElement().satisfies(n -> {
				assertThat(n).contains("\"topic\":\"catalog\"");
				assertThat(n).contains("\"type\":\"CATEGORY_CHANGED\"");
			}));
	}

	@Test
	void signalsTheCatalogTopicWhenAModifierGroupChanges(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var groupId = UUID.randomUUID();

		scenario.publish(new ModifierGroupChanged(groupId, null, null, null, null, NOW))
			.andWaitForStateChange(() -> probe.countMatching(groupId.toString()), count -> count >= 1)
			.andVerify(count -> assertThat(probe.matching(groupId.toString())).singleElement().satisfies(n -> {
				assertThat(n).contains("\"topic\":\"catalog\"");
				assertThat(n).contains("\"type\":\"MODIFIER_GROUP_CHANGED\"");
			}));
	}

	@Test
	void deliveringTheSameCategoryEventTwiceSignalsBothTimesWithoutFailing(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var categoryId = UUID.randomUUID();
		var event = new CategoryChanged(categoryId, CatalogChangeKind.UPDATED, null, null, NOW);

		scenario.publish(event)
			.andWaitForStateChange(() -> probe.countMatching(categoryId.toString()), count -> count >= 1)
			.andVerify(count -> {
			});
		scenario.publish(event)
			.andWaitForStateChange(() -> probe.countMatching(categoryId.toString()), count -> count >= 2)
			.andVerify(count -> assertThat(probe.matching(categoryId.toString())).hasSizeGreaterThanOrEqualTo(2));
	}

}
