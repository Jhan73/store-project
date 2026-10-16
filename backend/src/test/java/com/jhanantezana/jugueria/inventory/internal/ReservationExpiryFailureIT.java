package com.jhanantezana.jugueria.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.catalog.CatalogApi;
import com.jhanantezana.jugueria.inventory.InventoryApi;
import com.jhanantezana.jugueria.inventory.ReservationExpired;
import com.jhanantezana.jugueria.inventory.StockLine;
import com.jhanantezana.jugueria.shared.Jobs;
import com.jhanantezana.testsupport.InventoryTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ReservationExpiryFailureIT {

	static final Duration TTL = Duration.ofMinutes(15);

	@Autowired
	InventoryApi inventory;

	@Autowired
	StockService stock;

	@Autowired
	ReservationExpiry expiry;

	@Autowired
	ReservationExpiryJob job;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	ApplicationEvents events;

	@MockitoSpyBean
	CatalogApi catalog;

	@AfterEach
	void cleanUp() {
		InventoryTables.clean(jdbc);
	}

	@Test
	void aReferenceThatCannotBeExpiredDoesNotBlockTheOthers() {
		var poisoned = trackedWithStock(1);
		var healthy = trackedWithStock(1);
		var poisonedOrder = reserveAndLapse(poisoned);
		var healthyOrder = reserveAndLapse(healthy);
		doThrow(new IllegalStateException("catalog is down")).when(catalog)
			.setProductAvailability(eq(poisoned), any(Boolean.class));

		var expired = Jobs.drain(expiry::expireBatch);

		assertThat(expired).isEqualTo(1);
		assertThat(stock.get(healthy).reserved()).isZero();
		assertThat(stock.get(poisoned).reserved()).isEqualTo(1);
		assertThat(status(poisonedOrder)).isEqualTo("RESERVED");
		assertThat(events.stream(ReservationExpired.class)).singleElement()
			.satisfies(event -> assertThat(event.referenceId()).isEqualTo(healthyOrder));
	}

	@Test
	void theScheduledJobSurvivesAFailingReferenceAndRetriesItNextRun() {
		var poisoned = trackedWithStock(1);
		var order = reserveAndLapse(poisoned);
		doThrow(new IllegalStateException("catalog is down")).doCallRealMethod()
			.when(catalog)
			.setProductAvailability(eq(poisoned), any(Boolean.class));

		job.run();
		assertThat(status(order)).isEqualTo("RESERVED");
		job.run();

		assertThat(status(order)).isEqualTo("EXPIRED");
		assertThat(stock.get(poisoned).reserved()).isZero();
	}

	private UUID reserveAndLapse(UUID product) {
		var order = UUID.randomUUID();
		inventory.reserve(order, List.of(new StockLine(product, 1)), TTL);
		jdbc.sql("update inventory.stock_reservation set expires_at = now() - interval '1 minute' where reference_id = ?")
			.param(order)
			.update();
		return order;
	}

	private String status(UUID order) {
		return jdbc.sql("select status from inventory.stock_reservation where reference_id = ?")
			.param(order)
			.query(String.class)
			.single();
	}

	private UUID trackedWithStock(int quantity) {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, quantity, null);
		return product;
	}

}
