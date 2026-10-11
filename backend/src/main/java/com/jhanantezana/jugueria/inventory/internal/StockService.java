package com.jhanantezana.jugueria.inventory.internal;

import static com.jhanantezana.jugueria.inventory.internal.StockChanges.utc;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.catalog.CatalogApi;
import com.jhanantezana.jugueria.inventory.InventoryError;
import com.jhanantezana.jugueria.inventory.StockAdjusted;
import com.jhanantezana.jugueria.inventory.StockChangeKind;
import com.jhanantezana.jugueria.inventory.StockSnapshot;
import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.store.ReasonType;
import com.jhanantezana.jugueria.store.StoreApi;

@Service
public class StockService {

	private static final String OTHER_REASON = "other";

	private static final String SELECT_ITEM = """
			SELECT product_id, tracked, on_hand, reserved, low_stock_threshold FROM inventory.stock_item
			""";

	private final JdbcClient jdbc;

	private final StockChanges changes;

	private final CatalogApi catalog;

	private final StoreApi store;

	private final ApplicationEventPublisher events;

	private final CurrentActor currentActor;

	private final Clock clock;

	StockService(JdbcClient jdbc, StockChanges changes, CatalogApi catalog, StoreApi store,
			ApplicationEventPublisher events, CurrentActor currentActor, Clock clock) {
		this.jdbc = jdbc;
		this.changes = changes;
		this.catalog = catalog;
		this.store = store;
		this.events = events;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	/** Turns stock tracking on or off for a product and sets its low-stock threshold. */
	@Transactional
	public StockItemView track(UUID productId, boolean tracked, int lowStockThreshold) {
		if (!catalog.productExists(productId)) {
			throw new BusinessException(InventoryError.PRODUCT_NOT_FOUND, "Product not found");
		}
		var now = Instant.now(clock);
		// An untracked placeholder row to lock, so "before" is always readable and two first calls serialize.
		jdbc.sql("""
				INSERT INTO inventory.stock_item (product_id, tracked, on_hand, reserved, low_stock_threshold,
					created_at, updated_at)
				VALUES (:product, false, 0, 0, 0, :now, :now) ON CONFLICT DO NOTHING
				""").param("product", productId).param("now", utc(now)).update();
		var before = lock(productId).orElseThrow();
		var updated = jdbc.sql("""
				UPDATE inventory.stock_item
				SET tracked = :tracked, low_stock_threshold = :threshold, version = version + 1, updated_at = :now
				WHERE product_id = :product AND (:tracked OR reserved = 0)
				""")
			.param("tracked", tracked)
			.param("threshold", lowStockThreshold)
			.param("now", utc(now))
			.param("product", productId)
			.update();
		if (updated == 0) {
			throw new BusinessException(InventoryError.STOCK_RESERVED,
					"Units of this product are reserved; stock tracking cannot be turned off yet");
		}
		var after = find(productId).orElseThrow();
		// Stock owns the flag only while a product is tracked; a manual 86 on a product with stock is left alone.
		if (tracked) {
			if (after.available() <= 0) {
				catalog.setProductAvailability(productId, false);
			}
			else if (!before.tracked()) {
				catalog.setProductAvailability(productId, true);
			}
		}
		else if (before.tracked() && before.available() <= 0) {
			catalog.setProductAvailability(productId, true);
		}
		if (!snapshot(before).equals(snapshot(after))) {
			publish(productId, StockChangeKind.TRACKING_CHANGED, before, after, null, null, now);
		}
		return after;
	}

	/** Adds purchased units to a tracked product. */
	@Transactional
	public StockItemView recordEntry(UUID productId, int quantity, @Nullable String note) {
		if (quantity <= 0) {
			throw new BusinessException(InventoryError.INVALID_QUANTITY, "An entry needs a positive quantity");
		}
		var text = blankToNull(note);
		var before = lockTracked(productId);
		changes.apply(productId, StockMovementType.ENTRY, quantity, 0, null, null, text);
		var after = find(productId).orElseThrow();
		publish(productId, StockChangeKind.ENTRY, before, after, null, text, Instant.now(clock));
		return after;
	}

	/** Corrects a tracked product's quantity by a signed amount, for a reason from the store's list. */
	@Transactional
	public StockItemView adjust(UUID productId, int quantityDelta, String reasonCode, @Nullable String note) {
		if (quantityDelta == 0) {
			throw new BusinessException(InventoryError.INVALID_QUANTITY, "An adjustment must change the quantity");
		}
		var text = blankToNull(note);
		var reason = requireReason(reasonCode, text);
		var before = lockTracked(productId);
		changes.apply(productId, StockMovementType.ADJUSTMENT, quantityDelta, 0, null, reason, text);
		var after = find(productId).orElseThrow();
		publish(productId, StockChangeKind.ADJUSTMENT, before, after, reason, text, Instant.now(clock));
		return after;
	}

	private static @Nullable String blankToNull(@Nullable String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

	@Transactional(readOnly = true)
	public StockItemView get(UUID productId) {
		return find(productId).orElseThrow(() -> notFound());
	}

	@Transactional(readOnly = true)
	public List<StockItemView> list() {
		return jdbc.sql(SELECT_ITEM + " WHERE tracked ORDER BY product_id")
			.query((rs, row) -> view(rs))
			.list();
	}

	/** Tracked products whose sellable quantity is at or below their threshold, emptiest first. */
	@Transactional(readOnly = true)
	public List<StockItemView> lowStock() {
		return jdbc
			.sql(SELECT_ITEM + " WHERE tracked AND on_hand - reserved <= low_stock_threshold "
					+ "ORDER BY on_hand - reserved, product_id")
			.query((rs, row) -> view(rs))
			.list();
	}

	@Transactional(readOnly = true)
	public Page<MovementView> movements(UUID productId, Pageable pageable) {
		get(productId);
		var total = jdbc.sql("SELECT count(*) FROM inventory.stock_movement WHERE product_id = :product")
			.param("product", productId)
			.query(Long.class)
			.single();
		var content = jdbc.sql("""
				SELECT id, product_id, type, on_hand_delta, reserved_delta, reference_id, reason_code, note, actor_id,
					actor_role, occurred_at
				FROM inventory.stock_movement WHERE product_id = :product
				ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset
				""")
			.param("product", productId)
			.param("limit", pageable.getPageSize())
			.param("offset", pageable.getOffset())
			.query((rs, row) -> MovementView.from(rs))
			.list();
		return new PageImpl<>(content, pageable, total);
	}

	private String requireReason(String reasonCode, @Nullable String note) {
		var reason = store.reasons(ReasonType.STOCK_ADJUSTMENT)
			.stream()
			.filter(candidate -> candidate.active() && candidate.code().equalsIgnoreCase(reasonCode.trim()))
			.findFirst()
			.orElseThrow(() -> new BusinessException(InventoryError.INVALID_REASON,
					"The reason is not an active stock adjustment reason"));
		if (OTHER_REASON.equalsIgnoreCase(reason.code()) && (note == null || note.isBlank())) {
			throw new BusinessException(InventoryError.INVALID_REASON, "The reason \"Other\" needs a note");
		}
		return reason.code();
	}

	private StockSnapshot lockTracked(UUID productId) {
		var item = lock(productId).orElseThrow(StockService::notFound);
		if (!item.tracked()) {
			throw new BusinessException(InventoryError.PRODUCT_NOT_TRACKED, "The product is not stock-tracked");
		}
		return snapshot(item);
	}

	private Optional<StockItemView> lock(UUID productId) {
		return jdbc.sql(SELECT_ITEM + " WHERE product_id = :product FOR UPDATE")
			.param("product", productId)
			.query((rs, row) -> view(rs))
			.optional();
	}

	private Optional<StockItemView> find(UUID productId) {
		return jdbc.sql(SELECT_ITEM + " WHERE product_id = :product")
			.param("product", productId)
			.query((rs, row) -> view(rs))
			.optional();
	}

	private void publish(UUID productId, StockChangeKind kind, StockItemView before, StockItemView after,
			@Nullable String reason, @Nullable String note, Instant now) {
		publish(productId, kind, snapshot(before), after, reason, note, now);
	}

	private void publish(UUID productId, StockChangeKind kind, StockSnapshot before, StockItemView after,
			@Nullable String reason, @Nullable String note, Instant now) {
		events.publishEvent(new StockAdjusted(productId, kind, before, snapshot(after), reason, note,
				currentActor.id(), currentActor.role(), now));
	}

	private static StockSnapshot snapshot(StockItemView item) {
		return new StockSnapshot(item.tracked(), item.onHand(), item.reserved(), item.lowStockThreshold());
	}

	private static StockItemView view(java.sql.ResultSet rs) throws java.sql.SQLException {
		return StockItemView.of(rs.getObject("product_id", UUID.class), rs.getBoolean("tracked"),
				rs.getInt("on_hand"), rs.getInt("reserved"), rs.getInt("low_stock_threshold"));
	}

	private static BusinessException notFound() {
		return new BusinessException(InventoryError.STOCK_ITEM_NOT_FOUND, "The product has no stock record");
	}

}
