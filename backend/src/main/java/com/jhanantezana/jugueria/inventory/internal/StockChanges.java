package com.jhanantezana.jugueria.inventory.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.CatalogApi;
import com.jhanantezana.jugueria.inventory.InventoryError;
import com.jhanantezana.jugueria.inventory.StockDepleted;
import com.jhanantezana.jugueria.inventory.StockLow;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.Ids;

/**
 * The only door through which stock quantities change: one conditional update plus its ledger row, in the caller's
 * transaction. Callers handling several products must call it in ascending product order.
 */
@Component
class StockChanges {

	private record Balance(int onHand, int reserved, int lowStockThreshold) {
	}

	private final JdbcClient jdbc;

	private final Clock clock;

	private final CurrentActor currentActor;

	private final CatalogApi catalog;

	private final ApplicationEventPublisher events;

	StockChanges(JdbcClient jdbc, Clock clock, CurrentActor currentActor, CatalogApi catalog,
			ApplicationEventPublisher events) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.currentActor = currentActor;
		this.catalog = catalog;
		this.events = events;
	}

	/**
	 * Applies the deltas when the product is tracked and the result keeps {@code reserved <= on_hand}; anything else
	 * is a rejection. A SALE or RETURN already recorded for the same reference and product is skipped.
	 *
	 * @return false when the movement was already recorded
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	boolean apply(UUID productId, StockMovementType type, int onHandDelta, int reservedDelta,
			@Nullable UUID referenceId, @Nullable String reasonCode, @Nullable String note) {
		var now = Instant.now(clock);
		// The ledger row goes first: its unique index is what makes a redelivered sale or return a no-op.
		var recorded = jdbc.sql("""
				INSERT INTO inventory.stock_movement (id, product_id, type, on_hand_delta, reserved_delta, reference_id,
					reason_code, note, actor_id, actor_role, occurred_at)
				VALUES (:id, :product, :type, :onHand, :reserved, :reference, :reason, :note, :actor, :role, :now)
				ON CONFLICT DO NOTHING
				""")
			.param("id", Ids.newId())
			.param("product", productId)
			.param("type", type.name())
			.param("onHand", onHandDelta)
			.param("reserved", reservedDelta)
			.param("reference", referenceId)
			.param("reason", reasonCode)
			.param("note", note)
			.param("actor", currentActor.id())
			.param("role", currentActor.role() == null ? null : currentActor.role().name())
			.param("now", utc(now))
			.update();
		if (recorded == 0) {
			return false;
		}
		var balance = jdbc.sql("""
				UPDATE inventory.stock_item
				SET on_hand = on_hand + :onHand, reserved = reserved + :reserved, version = version + 1,
					updated_at = :now
				WHERE product_id = :product AND tracked
					AND on_hand + :onHand >= reserved + :reserved AND reserved + :reserved >= 0
				RETURNING on_hand, reserved, low_stock_threshold
				""")
			.param("onHand", onHandDelta)
			.param("reserved", reservedDelta)
			.param("now", utc(now))
			.param("product", productId)
			.query((rs, row) -> new Balance(rs.getInt("on_hand"), rs.getInt("reserved"),
					rs.getInt("low_stock_threshold")))
			.optional()
			.orElseThrow(() -> rejection(productId));
		reconcileAvailability(productId, balance, onHandDelta - reservedDelta, now);
		return true;
	}

	// Staff can still 86 a tracked product by hand; stock only moves the flag when it crosses zero.
	private void reconcileAvailability(UUID productId, Balance balance, int availableDelta, Instant now) {
		var after = balance.onHand() - balance.reserved();
		var before = after - availableDelta;
		if (before > 0 && after <= 0) {
			catalog.setProductAvailability(productId, false);
			events.publishEvent(new StockDepleted(productId, now));
		}
		else if (before <= 0 && after > 0) {
			catalog.setProductAvailability(productId, true);
		}
		if (before > balance.lowStockThreshold() && after <= balance.lowStockThreshold()) {
			events.publishEvent(new StockLow(productId, after, balance.lowStockThreshold(), now));
		}
	}

	private BusinessException rejection(UUID productId) {
		Optional<int[]> row = jdbc
			.sql("SELECT on_hand - reserved, CASE WHEN tracked THEN 1 ELSE 0 END FROM inventory.stock_item "
					+ "WHERE product_id = :product")
			.param("product", productId)
			.query((rs, i) -> new int[] { rs.getInt(1), rs.getInt(2) })
			.optional();
		if (row.isEmpty()) {
			return new BusinessException(InventoryError.STOCK_ITEM_NOT_FOUND, "The product has no stock record");
		}
		if (row.get()[1] == 0) {
			return new BusinessException(InventoryError.PRODUCT_NOT_TRACKED, "The product is not stock-tracked");
		}
		return new BusinessException(InventoryError.INSUFFICIENT_STOCK, "Not enough stock for product " + productId,
				Map.of("productId", productId.toString(), "available", row.get()[0]));
	}

	static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
