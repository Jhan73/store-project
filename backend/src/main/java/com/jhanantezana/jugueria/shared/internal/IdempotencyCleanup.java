package com.jhanantezana.jugueria.shared.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.shared.Jobs;

@Component
class IdempotencyCleanup {

	private final JdbcClient jdbc;

	private final Clock clock;

	IdempotencyCleanup(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// MATERIALIZED: inlined as an IN subquery, the planner may rescan it and delete past the LIMIT.
	@Transactional
	int deleteExpiredBatch() {
		return jdbc.sql("""
				WITH claimed AS MATERIALIZED (
					SELECT actor_id, key FROM shared.idempotency_key
					WHERE expires_at <= :now ORDER BY expires_at LIMIT :limit FOR UPDATE SKIP LOCKED)
				DELETE FROM shared.idempotency_key k USING claimed c
				WHERE k.actor_id = c.actor_id AND k.key = c.key
				""")
			.param("now", OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC))
			.param("limit", Jobs.BATCH_SIZE)
			.update();
	}

}
