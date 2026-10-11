package com.jhanantezana.jugueria.inventory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stock operations for the sales channels. Every method joins the caller's transaction, so a sale and its stock
 * change commit or roll back together. Lines for products that are not stock-tracked are ignored, so a caller can
 * pass a whole cart. Lines for the same product are summed. Several products are always handled in ascending
 * product order, so concurrent callers cannot deadlock. All operations are safe to repeat for the same reference.
 *
 * <p>
 * Rejections are {@code BusinessException}s: {@code inventory.insufficient-stock} (extra properties
 * {@code productId} and {@code available}) when a line asks for more than can still be sold, and
 * {@code inventory.reservation-not-active} when a commit finds its reservation expired or released.
 */
public interface InventoryApi {

	/**
	 * Holds the units for {@code referenceId} until the returned moment. Reserved units cannot be sold by any
	 * channel, and an expired reservation is released by a scheduled job, which also publishes
	 * {@link ReservationExpired}. The time to live must be positive.
	 */
	Instant reserve(UUID referenceId, List<StockLine> lines, Duration ttl);

	/** Turns the reference's live reservation into a sale. A reference with nothing reserved is a no-op. */
	void commit(UUID referenceId);

	/** Gives the reference's live reservation back to the shelf. A reference with nothing reserved is a no-op. */
	void release(UUID referenceId);

	/** Sells units that were not reserved, as an in-store ticket or quick sale does. */
	void deduct(UUID referenceId, List<StockLine> lines);

	/**
	 * Puts units of a voided or refunded sale back on the shelf; waste must not be returned. A return is applied once
	 * per reference and product, so the reference must be unique per refund or void: a second partial return under the
	 * same reference is silently skipped.
	 */
	void returnUnits(UUID referenceId, List<StockLine> lines);

}
