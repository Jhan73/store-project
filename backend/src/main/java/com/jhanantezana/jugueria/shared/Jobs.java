package com.jhanantezana.jugueria.shared;

import java.util.function.IntSupplier;

/**
 * Pattern for scheduled jobs: every task runs the job and claims at most {@link #BATCH_SIZE} rows per
 * transaction with {@code SELECT ... FOR UPDATE SKIP LOCKED LIMIT 100}, so concurrent tasks never take the same row.
 */
public final class Jobs {

	public static final int BATCH_SIZE = 100;

	private Jobs() {
	}

	/**
	 * Runs {@code batch} until it claims fewer than {@link #BATCH_SIZE} rows. Each call must be its own
	 * transaction (a call to a {@code @Transactional} bean method), so locks are held for one batch only.
	 *
	 * @return the total number of rows the batches claimed
	 */
	public static int drain(IntSupplier batch) {
		var total = 0;
		int claimed;
		do {
			claimed = batch.getAsInt();
			total += claimed;
		}
		while (claimed >= BATCH_SIZE);
		return total;
	}

}
