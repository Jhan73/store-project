package com.jhanantezana.jugueria.inventory.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.inventory.InventoryApi;
import com.jhanantezana.jugueria.inventory.InventoryError;
import com.jhanantezana.jugueria.inventory.StockLine;
import com.jhanantezana.jugueria.inventory.internal.StockReservations.Held;
import com.jhanantezana.jugueria.inventory.internal.StockReservations.Status;
import com.jhanantezana.jugueria.shared.BusinessException;

@Service
class InventoryApiImpl implements InventoryApi {

	// The text order of a UUID is PostgreSQL's order, so every caller locks rows in the same sequence.
	private static final Comparator<UUID> BY_PRODUCT_ID = Comparator.comparing(UUID::toString);

	private final StockChanges changes;

	private final StockReservations reservations;

	private final JdbcClient jdbc;

	private final Clock clock;

	InventoryApiImpl(StockChanges changes, StockReservations reservations, JdbcClient jdbc, Clock clock) {
		this.changes = changes;
		this.reservations = reservations;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	@Transactional
	public Instant reserve(UUID referenceId, List<StockLine> lines, Duration ttl) {
		if (ttl.isZero() || ttl.isNegative()) {
			throw new IllegalArgumentException("A reservation needs a positive time to live");
		}
		var now = Instant.now(clock);
		var expiresAt = now.plus(ttl);
		// Reservation rows first, stock rows after: the expiry sweep takes them in that order too.
		var held = new ArrayList<StockLine>();
		for (var line : trackedLines(lines, false)) {
			if (reservations.hold(referenceId, line.productId(), line.quantity(), expiresAt, now)) {
				held.add(line);
			}
		}
		var stillTracked = trackedIds(held.stream().map(StockLine::productId).toList(), true);
		for (var line : held) {
			if (stillTracked.contains(line.productId())) {
				changes.apply(line.productId(), StockMovementType.RESERVE, 0, line.quantity(), referenceId, null, null);
			}
			else {
				reservations.drop(referenceId, line.productId());
			}
		}
		return expiresAt;
	}

	@Override
	@Transactional
	public void commit(UUID referenceId) {
		var held = sorted(reservations.claim(referenceId, Status.COMMITTED, Instant.now(clock)));
		if (held.isEmpty()) {
			if (!reservations.has(referenceId, Status.COMMITTED)
					&& reservations.has(referenceId, Status.EXPIRED, Status.RELEASED)) {
				throw notActive(referenceId);
			}
			return;
		}
		if (reservations.anyLapsedAlongside(referenceId, held.stream().map(Held::createdAt).toList())) {
			throw notActive(referenceId);
		}
		for (var line : held) {
			changes.apply(line.productId(), StockMovementType.COMMIT, -line.quantity(), -line.quantity(), referenceId,
					null, null);
		}
	}

	@Override
	@Transactional
	public void release(UUID referenceId) {
		for (var line : sorted(reservations.claim(referenceId, Status.RELEASED, Instant.now(clock)))) {
			changes.apply(line.productId(), StockMovementType.RELEASE, 0, -line.quantity(), referenceId, null, null);
		}
	}

	@Override
	@Transactional
	public void deduct(UUID referenceId, List<StockLine> lines) {
		for (var line : trackedLines(lines, true)) {
			changes.apply(line.productId(), StockMovementType.SALE, -line.quantity(), 0, referenceId, null, null);
		}
	}

	@Override
	@Transactional
	public void deductAllowingShortfall(UUID referenceId, List<StockLine> lines) {
		for (var line : trackedLines(lines, true)) {
			changes.apply(line.productId(), StockMovementType.SALE, -line.quantity(), 0, referenceId, null, null, true);
		}
	}

	@Override
	@Transactional
	public void recordWaste(UUID referenceId, List<StockLine> lines) {
		for (var line : trackedLines(lines, true)) {
			changes.apply(line.productId(), StockMovementType.WASTE, -line.quantity(), 0, referenceId, null, null, true);
		}
	}

	@Override
	@Transactional
	public void returnUnits(UUID referenceId, List<StockLine> lines) {
		var sold = soldUnits(referenceId);
		for (var line : trackedLines(lines, true)) {
			var units = Math.min(line.quantity(), sold.getOrDefault(line.productId(), 0));
			if (units > 0) {
				changes.apply(line.productId(), StockMovementType.RETURN, units, 0, referenceId, null, null);
			}
		}
	}

	// A return puts back only what the reference's own sale took, so a reference that sold nothing returns nothing.
	private Map<UUID, Integer> soldUnits(UUID referenceId) {
		var sold = new java.util.HashMap<UUID, Integer>();
		jdbc.sql("SELECT product_id, -on_hand_delta AS units FROM inventory.stock_movement "
				+ "WHERE type = 'SALE' AND reference_id = :reference")
			.param("reference", referenceId)
			.query((rs, row) -> Map.entry(rs.getObject("product_id", UUID.class), rs.getInt("units")))
			.list()
			.forEach(entry -> sold.put(entry.getKey(), entry.getValue()));
		return sold;
	}

	/** The lines of stock-tracked products, one per product, in ascending product order. */
	private List<StockLine> trackedLines(List<StockLine> lines, boolean lock) {
		var quantities = new TreeMap<UUID, Integer>(BY_PRODUCT_ID);
		for (var line : lines) {
			quantities.merge(line.productId(), line.quantity(), Integer::sum);
		}
		var tracked = trackedIds(quantities.keySet(), lock);
		return quantities.entrySet()
			.stream()
			.filter(entry -> tracked.contains(entry.getKey()))
			.map((Map.Entry<UUID, Integer> entry) -> new StockLine(entry.getKey(), entry.getValue()))
			.toList();
	}

	// Locked in ascending order, so a product being untracked is waited for and then left out, never half-applied.
	private Set<UUID> trackedIds(Collection<UUID> ids, boolean lock) {
		if (ids.isEmpty()) {
			return Set.of();
		}
		return jdbc
			.sql("SELECT product_id FROM inventory.stock_item WHERE tracked AND product_id IN (:ids) ORDER BY product_id"
					+ (lock ? " FOR UPDATE" : ""))
			.param("ids", ids)
			.query(UUID.class)
			.set();
	}

	private static List<Held> sorted(List<Held> held) {
		return held.stream().sorted(Comparator.comparing(Held::productId, BY_PRODUCT_ID)).toList();
	}

	private static BusinessException notActive(UUID referenceId) {
		return new BusinessException(InventoryError.RESERVATION_NOT_ACTIVE,
				"The reservation of " + referenceId + " expired or was released");
	}

}
