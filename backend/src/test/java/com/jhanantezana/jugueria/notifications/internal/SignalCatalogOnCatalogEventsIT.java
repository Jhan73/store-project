package com.jhanantezana.jugueria.notifications.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Set;
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
import com.jhanantezana.jugueria.catalog.AvailabilityChanged;
import com.jhanantezana.jugueria.catalog.AvailabilityTarget;
import com.jhanantezana.jugueria.catalog.CatalogChangeKind;
import com.jhanantezana.jugueria.catalog.CategoryChanged;
import com.jhanantezana.jugueria.catalog.ModifierGroupChanged;
import com.jhanantezana.jugueria.catalog.ProductChanged;
import com.jhanantezana.jugueria.catalog.ProductSnapshot;
import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.catalog.StationChanged;

// identity and shared are force-included for the same reason as the store-status signal tests.
@ApplicationModuleTest(extraIncludes = { "identity", "shared" })
@TestPropertySource(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class SignalCatalogOnCatalogEventsIT {

	static final Instant NOW = Instant.parse("2026-09-29T09:00:00Z");

	static final Currency PEN = Currency.getInstance("PEN");

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
	void signalsTheCatalogTopicWhenAProductChanges(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var productId = UUID.randomUUID();
		var snapshot = new ProductSnapshot("Mango", null, UUID.randomUUID(), Money.of("5.00", PEN), 0, false, true,
				null, Set.of(), List.of());

		scenario.publish(new ProductChanged(productId, null, snapshot, null, null, NOW))
			.andWaitForStateChange(() -> probe.countMatching(productId.toString()), count -> count >= 1)
			.andVerify(count -> assertThat(probe.matching(productId.toString())).singleElement().satisfies(n -> {
				assertThat(n).contains("\"topic\":\"catalog\"");
				assertThat(n).contains("\"type\":\"PRODUCT_CHANGED\"");
			}));
	}

	@Test
	void signalsTheCatalogTopicWhenAProductIsMarkedUnavailable(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var productId = UUID.randomUUID();

		scenario.publish(new AvailabilityChanged(productId, AvailabilityTarget.PRODUCT, false, null, null, NOW))
			.andWaitForStateChange(() -> probe.countMatching(productId.toString()), count -> count >= 1)
			.andVerify(count -> assertThat(probe.matching(productId.toString())).singleElement().satisfies(n -> {
				assertThat(n).contains("\"topic\":\"catalog\"");
				assertThat(n).contains("\"type\":\"PRODUCT_AVAILABILITY_CHANGED\"");
				assertThat(n).contains("\"productId\"");
			}));
	}

	@Test
	void signalsTheCatalogTopicWhenAnOptionIsMarkedUnavailable(Scenario scenario) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var optionId = UUID.randomUUID();

		scenario.publish(new AvailabilityChanged(optionId, AvailabilityTarget.MODIFIER_OPTION, false, null, null, NOW))
			.andWaitForStateChange(() -> probe.countMatching(optionId.toString()), count -> count >= 1)
			.andVerify(count -> assertThat(probe.matching(optionId.toString())).singleElement().satisfies(n -> {
				assertThat(n).contains("\"type\":\"OPTION_AVAILABILITY_CHANGED\"");
				assertThat(n).contains("\"optionId\"");
			}));
	}

	@Test
	void deliveringTheSameStationEventTwiceSignalsBothTimesWithoutFailing(Scenario scenario) throws SQLException {
		var id = UUID.randomUUID();

		assertSignalsTwice(scenario, new StationChanged(id, CatalogChangeKind.UPDATED, null, null, NOW), id);
	}

	@Test
	void deliveringTheSameProductEventTwiceSignalsBothTimesWithoutFailing(Scenario scenario) throws SQLException {
		var id = UUID.randomUUID();
		var snapshot = new ProductSnapshot("Mango", null, UUID.randomUUID(), Money.of("5.00", PEN), 0, false, true,
				null, Set.of(), List.of());

		assertSignalsTwice(scenario, new ProductChanged(id, null, snapshot, null, null, NOW), id);
	}

	@Test
	void deliveringTheSameModifierGroupEventTwiceSignalsBothTimesWithoutFailing(Scenario scenario)
			throws SQLException {
		var id = UUID.randomUUID();

		assertSignalsTwice(scenario, new ModifierGroupChanged(id, null, null, null, null, NOW), id);
	}

	@Test
	void deliveringTheSameAvailabilityEventTwiceSignalsBothTimesWithoutFailing(Scenario scenario)
			throws SQLException {
		var id = UUID.randomUUID();

		assertSignalsTwice(scenario, new AvailabilityChanged(id, AvailabilityTarget.PRODUCT, false, null, null, NOW),
				id);
	}

	private void assertSignalsTwice(Scenario scenario, Object event, UUID id) throws SQLException {
		probe = new AppEventsProbe(connectionDetails);
		var marker = id.toString();

		scenario.publish(event).andWaitForStateChange(() -> probe.countMatching(marker), count -> count >= 1).andVerify(count -> {
		});
		scenario.publish(event)
			.andWaitForStateChange(() -> probe.countMatching(marker), count -> count >= 2)
			.andVerify(count -> assertThat(probe.matching(marker)).hasSizeGreaterThanOrEqualTo(2));
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
