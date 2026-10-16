package com.jhanantezana.jugueria.inventory.internal;

import static com.jhanantezana.jugueria.inventory.internal.StockChanges.utc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.shared.Ids;
import com.jhanantezana.jugueria.shared.Jobs;

@Component
class StockReservations {

	enum Status {

		RESERVED, COMMITTED, RELEASED, EXPIRED

	}

	record Held(UUID referenceId, UUID productId, int quantity, OffsetDateTime createdAt) {
	}

	private final JdbcClient jdbc;

	StockReservations(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** @return false when the reference already holds (or has committed) this product, so a repeat does nothing */
	boolean hold(UUID referenceId, UUID productId, int quantity, Instant expiresAt, Instant now) {
		return jdbc.sql("""
				INSERT INTO inventory.stock_reservation (id, reference_id, product_id, quantity, status, expires_at,
					created_at, updated_at)
				VALUES (:id, :reference, :product, :quantity, 'RESERVED', :expiresAt, :now, :now)
				ON CONFLICT DO NOTHING
				""")
			.param("id", Ids.newId())
			.param("reference", referenceId)
			.param("product", productId)
			.param("quantity", quantity)
			.param("expiresAt", utc(expiresAt))
			.param("now", utc(now))
			.update() == 1;
	}

	/** Moves every live reservation of the reference to {@code to}; the status guard makes concurrent claims exclusive. */
	List<Held> claim(UUID referenceId, Status to, Instant now) {
		return jdbc.sql("""
				UPDATE inventory.stock_reservation SET status = :to, updated_at = :now
				WHERE reference_id = :reference AND status = 'RESERVED'
				RETURNING reference_id, product_id, quantity, created_at
				""")
			.param("to", to.name())
			.param("now", utc(now))
			.param("reference", referenceId)
			.query((rs, row) -> held(rs))
			.list();
	}

	/** Removes a hold taken by the caller's own transaction, before anyone else could see it. */
	void drop(UUID referenceId, UUID productId) {
		jdbc.sql("DELETE FROM inventory.stock_reservation WHERE reference_id = :reference AND product_id = :product "
				+ "AND status = 'RESERVED'")
			.param("reference", referenceId)
			.param("product", productId)
			.update();
	}

	/**
	 * The references with a lapsed reservation, oldest first. Runs in autocommit, so it only skips rows locked at
	 * that instant; claimLapsed does the real claiming.
	 */
	Set<UUID> lapsedReferences(Instant now) {
		return new LinkedHashSet<>(jdbc.sql("""
				SELECT reference_id FROM inventory.stock_reservation
				WHERE status = 'RESERVED' AND expires_at <= :now ORDER BY expires_at, id LIMIT :limit
				FOR UPDATE SKIP LOCKED
				""")
			.param("now", utc(now))
			.param("limit", Jobs.BATCH_SIZE)
			.query(UUID.class)
			.list());
	}

	/** Claims the reference's lapsed reservations, skipping rows another task already holds. */
	List<Held> claimLapsed(UUID referenceId, Instant now) {
		return jdbc.sql("""
				WITH claimed AS MATERIALIZED (
					SELECT id FROM inventory.stock_reservation
					WHERE reference_id = :reference AND status = 'RESERVED' AND expires_at <= :now ORDER BY id
					FOR UPDATE SKIP LOCKED)
				UPDATE inventory.stock_reservation r SET status = 'EXPIRED', updated_at = :now
				FROM claimed c WHERE r.id = c.id
				RETURNING r.reference_id, r.product_id, r.quantity, r.created_at
				""")
			.param("reference", referenceId)
			.param("now", utc(now))
			.query((rs, row) -> held(rs))
			.list();
	}

	boolean has(UUID referenceId, Status... statuses) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM inventory.stock_reservation WHERE reference_id = :reference "
				+ "AND status IN (:statuses))")
			.param("reference", referenceId)
			.param("statuses", java.util.Arrays.stream(statuses).map(Status::name).toList())
			.query(Boolean.class)
			.single();
	}

	/** True when a sibling reserved by the same call already lapsed, so the order's stock is no longer all held. */
	boolean anyLapsedAlongside(UUID referenceId, Collection<OffsetDateTime> createdAt) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM inventory.stock_reservation WHERE reference_id = :reference "
				+ "AND created_at IN (:createdAt) AND status IN ('EXPIRED', 'RELEASED'))")
			.param("reference", referenceId)
			.param("createdAt", createdAt)
			.query(Boolean.class)
			.single();
	}

	private static Held held(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new Held(rs.getObject("reference_id", UUID.class), rs.getObject("product_id", UUID.class),
				rs.getInt("quantity"), rs.getObject("created_at", OffsetDateTime.class));
	}

}
