package com.jhanantezana.jugueria.shared;

import java.util.function.IntSupplier;

/**
 * Pattern for scheduled jobs: every task runs the job and claims at most {@link #BATCH_SIZE} rows per
 * transaction with {@code SELECT ... FOR UPDATE SKIP LOCKED LIMIT 100}, so concurrent tasks never take the same row.
 */
public final class Jobs {

	public static final int BATCH_SIZE = 100;

	/** A run stops here; whatever is left waits for the next run, so a backlog cannot monopolize the scheduler. */
	public static final int MAX_BATCHES_PER_RUN = 10;

	private Jobs() {
	}

	/**
	 * Runs {@code batch} until it claims fewer than {@link #BATCH_SIZE} rows or {@link #MAX_BATCHES_PER_RUN} batches
	 * have run. Each call must be its own transaction (a call to a {@code @Transactional} bean method), so locks are
	 * held for one batch only.
	 *
	 * @return the total number of rows the batches claimed
	 */
	public static int drain(IntSupplier batch) {
		var total = 0;
		for (var run = 0; run < MAX_BATCHES_PER_RUN; run++) {
			var claimed = batch.getAsInt();
			total += claimed;
			if (claimed < BATCH_SIZE) {
				break;
			}
		}
		return total;
	}

}
