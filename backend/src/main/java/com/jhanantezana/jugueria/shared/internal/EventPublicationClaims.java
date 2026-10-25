package com.jhanantezana.jugueria.shared.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.shared.Jobs;

// Claims run in their own transaction and only stamp rows: the resubmission itself happens after the commit, so no
// row lock is held while a listener runs. MATERIALIZED: inlined as an IN subquery, the planner may rescan it and
// update past the LIMIT.
@Component
class EventPublicationClaims {

	// previousClaim is null for a publication nobody had claimed before.
	record Publication(UUID id, String listenerId, Instant publishedAt, int attempts, Instant previousClaim) {
	}

	record Claim(List<Publication> toResubmit, List<Publication> deadLettered) {
	}

	private static final String PENDING = """
			SELECT id, claimed_at FROM modulith.event_publication
			WHERE completion_date IS NULL
			  AND publication_date < :cutoff AND (claimed_at IS NULL OR claimed_at < :cutoff)
			  AND coalesce(completion_attempts, 0) %s :maxAttempts
			ORDER BY publication_date LIMIT :limit FOR UPDATE SKIP LOCKED
			""";

	private final JdbcClient jdbc;

	private final Clock clock;

	private final EventResubmissionProperties properties;

	EventPublicationClaims(JdbcClient jdbc, Clock clock, EventResubmissionProperties properties) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.properties = properties;
	}

	// A RESUBMITTED or PROCESSING row that outlived the lease belongs to a delivery that never answered (its task died),
	// so it goes back to FAILED, the state the library resubmits from.
	@Transactional
	Claim claimBatch() {
		var now = Instant.now(clock);
		var parked = jdbc.sql("""
				WITH due AS MATERIALIZED (%s),
				moved AS (DELETE FROM modulith.event_publication p USING due WHERE p.id = due.id
				  RETURNING p.id, p.listener_id, p.event_type, p.serialized_event, p.publication_date,
				    coalesce(p.completion_attempts, 0) AS attempts, due.claimed_at)
				INSERT INTO modulith.event_publication_dead_letter
				  (id, listener_id, event_type, serialized_event, publication_date, completion_attempts, dead_lettered_at)
				SELECT id, listener_id, event_type, serialized_event, publication_date, attempts, :now FROM moved
				RETURNING id, listener_id, publication_date, completion_attempts AS attempts, NULL::timestamptz AS claimed_at
				""".formatted(PENDING.formatted(">=")))
			.params(parameters(now))
			.query(EventPublicationClaims::publication)
			.list();
		var claimed = jdbc.sql("""
				WITH due AS MATERIALIZED (%s)
				UPDATE modulith.event_publication p SET claimed_at = :now,
				  status = CASE WHEN p.status IN ('RESUBMITTED', 'PROCESSING') THEN 'FAILED' ELSE p.status END
				FROM due WHERE p.id = due.id
				RETURNING p.id, p.listener_id, p.publication_date, coalesce(p.completion_attempts, 0) AS attempts,
				  due.claimed_at
				""".formatted(PENDING.formatted("<")))
			.params(parameters(now))
			.query(EventPublicationClaims::publication)
			.list();
		return new Claim(claimed, parked);
	}

	private Map<String, Object> parameters(Instant now) {
		return Map.of("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC), "cutoff",
				OffsetDateTime.ofInstant(now.minus(properties.minAge()), ZoneOffset.UTC), "maxAttempts",
				properties.maxAttempts(), "limit", Jobs.BATCH_SIZE);
	}

	private static Publication publication(ResultSet rs, int row) throws SQLException {
		var previous = rs.getObject("claimed_at", OffsetDateTime.class);
		return new Publication(rs.getObject("id", UUID.class), rs.getString("listener_id"),
				rs.getObject("publication_date", OffsetDateTime.class).toInstant(), rs.getInt("attempts"),
				previous == null ? null : previous.toInstant());
	}

}
