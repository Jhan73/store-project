package com.jhanantezana.jugueria.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.jugueria.inventory.InventoryApi;
import com.jhanantezana.jugueria.inventory.InventoryError;
import com.jhanantezana.jugueria.inventory.ReservationExpired;
import com.jhanantezana.jugueria.inventory.StockLine;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.Jobs;
import com.jhanantezana.testsupport.InventoryTables;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ReservationExpiryIT {

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
	ScheduledTaskHolder scheduledTasks;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DataSource dataSource;

	@Autowired
	ApplicationEvents events;

	@AfterEach
	void cleanUp() {
		InventoryTables.clean(jdbc);
	}

	@Test
	void expiresALapsedReservationAndGivesItsUnitsBack() {
		var product = trackedWithStock(2);
		var order = UUID.randomUUID();
		inventory.reserve(order, List.of(new StockLine(product, 2)), TTL);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isFalse();
		lapse(order);

		var expired = Jobs.drain(expiry::expireBatch);

		assertThat(expired).isEqualTo(1);
		var item = stock.get(product);
		assertThat(item.reserved()).isZero();
		assertThat(item.onHand()).isEqualTo(2);
		assertThat(InventoryTables.isAvailable(jdbc, product)).isTrue();
		assertThat(ledger(product)).containsExactly("0/2 RESERVE", "0/-2 EXPIRE");
		assertThat(events.stream(ReservationExpired.class)).singleElement()
			.satisfies(event -> assertThat(event.referenceId()).isEqualTo(order));
	}

	@Test
	void leavesAReservationThatIsStillLive() {
		var product = trackedWithStock(2);
		inventory.reserve(UUID.randomUUID(), List.of(new StockLine(product, 1)), TTL);

		var expired = Jobs.drain(expiry::expireBatch);

		assertThat(expired).isZero();
		assertThat(stock.get(product).reserved()).isEqualTo(1);
		assertThat(events.stream(ReservationExpired.class)).isEmpty();
	}

	@Test
	void anExpiredReservationCanNoLongerBeCommitted() {
		var product = trackedWithStock(2);
		var order = UUID.randomUUID();
		inventory.reserve(order, List.of(new StockLine(product, 1)), TTL);
		lapse(order);
		Jobs.drain(expiry::expireBatch);

		assertThatThrownBy(() -> inventory.commit(order)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(InventoryError.RESERVATION_NOT_ACTIVE));
		assertThat(stock.get(product).onHand()).isEqualTo(2);
	}

	@Test
	void anOrderWhoseLinesPartlyLapsedIsNeitherCommittedHalfNorLost() {
		var first = trackedWithStock(2);
		var second = trackedWithStock(2);
		var order = UUID.randomUUID();
		inventory.reserve(order, List.of(new StockLine(first, 1), new StockLine(second, 1)), TTL);
		jdbc.sql("""
				update inventory.stock_reservation set status = 'EXPIRED', expires_at = now() - interval '1 minute'
				where reference_id = ? and product_id = ?
				""").param(order).param(first).update();

		assertThatThrownBy(() -> inventory.commit(order)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.errorCode()).isEqualTo(InventoryError.RESERVATION_NOT_ACTIVE));

		assertThat(stock.get(second).onHand()).isEqualTo(2);
		assertThat(jdbc.sql("select status from inventory.stock_reservation where reference_id = ? and product_id = ?")
			.param(order)
			.param(second)
			.query(String.class)
			.single()).isEqualTo("RESERVED");
	}

	@Test
	void anOrderCanReserveAgainAfterItsReservationExpired() {
		var product = trackedWithStock(2);
		var order = UUID.randomUUID();
		inventory.reserve(order, List.of(new StockLine(product, 1)), TTL);
		lapse(order);
		Jobs.drain(expiry::expireBatch);

		inventory.reserve(order, List.of(new StockLine(product, 1)), TTL);
		inventory.commit(order);

		assertThat(stock.get(product).onHand()).isEqualTo(1);
	}

	@Test
	void drainsMoreLapsedReservationsThanOneBatchHolds() {
		var product = trackedWithStock(Jobs.BATCH_SIZE + 50);
		for (var i = 0; i < Jobs.BATCH_SIZE + 50; i++) {
			var order = UUID.randomUUID();
			inventory.reserve(order, List.of(new StockLine(product, 1)), TTL);
			lapse(order);
		}

		assertThat(expiry.expireBatch()).isEqualTo(Jobs.BATCH_SIZE);
		assertThat(Jobs.drain(expiry::expireBatch)).isEqualTo(50);
		assertThat(stock.get(product).reserved()).isZero();
	}

	@Test
	void theScheduledJobSweepsLapsedReservations() {
		var product = trackedWithStock(1);
		var order = UUID.randomUUID();
		inventory.reserve(order, List.of(new StockLine(product, 1)), TTL);
		lapse(order);

		job.run();

		assertThat(stock.get(product).reserved()).isZero();
	}

	@Test
	void theJobRunsEveryMinuteAndStartsAfterOneMinute() {
		assertThat(scheduledTasks.getScheduledTasks()).anyMatch(
				task -> task.getTask() instanceof FixedDelayTask delay && delay.getIntervalDuration().equals(Duration.ofMinutes(1))
						&& delay.getInitialDelayDuration().equals(Duration.ofMinutes(1)));
	}

	// A second task running the same job must skip rows another task has claimed, never wait on them.
	@Test
	void skipsReservationsAnotherTaskHasAlreadyClaimed() throws SQLException {
		var product = trackedWithStock(Jobs.BATCH_SIZE + 50);
		for (var i = 0; i < Jobs.BATCH_SIZE + 50; i++) {
			var order = UUID.randomUUID();
			inventory.reserve(order, List.of(new StockLine(product, 1)), TTL);
			lapse(order);
		}
		try (var otherTask = dataSource.getConnection()) {
			otherTask.setAutoCommit(false);
			try (var claim = otherTask.createStatement()) {
				claim.execute("SELECT 1 FROM inventory.stock_reservation WHERE status = 'RESERVED' "
						+ "AND expires_at <= now() ORDER BY expires_at, id LIMIT " + Jobs.BATCH_SIZE
						+ " FOR UPDATE SKIP LOCKED");

				var expired = Jobs.drain(expiry::expireBatch);

				assertThat(expired).isEqualTo(50);
				assertThat(stock.get(product).reserved()).isEqualTo(Jobs.BATCH_SIZE);
			}
			finally {
				otherTask.rollback();
			}
		}
	}

	private UUID trackedWithStock(int quantity) {
		var product = InventoryTables.aProduct(jdbc);
		stock.track(product, true, 0);
		stock.recordEntry(product, quantity, null);
		return product;
	}

	private void lapse(UUID order) {
		jdbc.sql("update inventory.stock_reservation set expires_at = now() - interval '1 minute' where reference_id = ?")
			.param(order)
			.update();
	}

	private List<String> ledger(UUID product) {
		return jdbc.sql("""
				select on_hand_delta || '/' || reserved_delta || ' ' || type from inventory.stock_movement
				where product_id = ? and type in ('RESERVE', 'EXPIRE') order by occurred_at, id
				""").param(product).query(String.class).list();
	}

}
