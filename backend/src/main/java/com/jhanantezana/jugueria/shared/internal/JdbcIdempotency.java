package com.jhanantezana.jugueria.shared.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.Idempotency;
import com.jhanantezana.jugueria.shared.StoredResponse;

@Component
class JdbcIdempotency implements Idempotency {

	// Spring's PostgreSQL translator leaves lock_timeout (lock_not_available) uncategorized.
	private static final String LOCK_NOT_AVAILABLE = "55P03";

	private static final int MAX_ATTEMPTS = 3;

	private final JdbcClient jdbc;

	private final Clock clock;

	private final IdempotencyProperties properties;

	JdbcIdempotency(JdbcClient jdbc, Clock clock, IdempotencyProperties properties) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.properties = properties;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Registration register(UUID actorId, UUID key, String requestHash) {
		var now = Instant.now(clock);
		var callerLockTimeout = jdbc.sql("SELECT current_setting('lock_timeout')").query(String.class).single();
		setLockTimeout(properties.lockTimeout().toMillis() + "ms");
		Registration registration = null;
		for (var attempt = 0; attempt < MAX_ATTEMPTS && registration == null; attempt++) {
			registration = tryRegister(actorId, key, requestHash, now);
		}
		// The setting would otherwise outlast this call and make the command's own row locks fail early.
		setLockTimeout(callerLockTimeout);
		if (registration == null) {
			throw new BusinessException(CommonError.IDEMPOTENCY_IN_PROGRESS,
					"A request with this Idempotency-Key is still being processed");
		}
		return registration;
	}

	// Null when the conflicting row was deleted by the cleanup before it could be read: the key is free again.
	private Registration tryRegister(UUID actorId, UUID key, String requestHash, Instant now) {
		int inserted;
		try {
			inserted = jdbc.sql("""
					INSERT INTO shared.idempotency_key (actor_id, key, request_hash, created_at, expires_at)
					VALUES (:actor, :key, :hash, :createdAt, :expiresAt)
					ON CONFLICT (actor_id, key) DO NOTHING
					""")
				.param("actor", actorId)
				.param("key", key)
				.param("hash", requestHash)
				.param("createdAt", utc(now))
				.param("expiresAt", utc(now.plus(properties.ttl())))
				.update();
		}
		catch (UncategorizedSQLException e) {
			if (!LOCK_NOT_AVAILABLE.equals(e.getSQLException().getSQLState())) {
				throw e;
			}
			throw new BusinessException(CommonError.IDEMPOTENCY_IN_PROGRESS,
					"A request with this Idempotency-Key is still being processed");
		}
		if (inserted == 1) {
			return new Registration.Fresh();
		}
		var found = jdbc.sql("""
				SELECT request_hash, response_status, response_body FROM shared.idempotency_key
				WHERE actor_id = :actor AND key = :key
				""")
			.param("actor", actorId)
			.param("key", key)
			.query((rs, row) -> new Previous(rs.getString("request_hash"), rs.getObject("response_status", Integer.class),
					rs.getString("response_body")))
			.optional();
		if (found.isEmpty()) {
			return null;
		}
		var previous = found.get();
		if (!previous.requestHash().equals(requestHash)) {
			throw new BusinessException(CommonError.IDEMPOTENCY_KEY_REUSED,
					"This Idempotency-Key was already used for a different request");
		}
		if (previous.status() == null || previous.body() == null) {
			throw new IllegalStateException("Idempotency key was committed without a stored response");
		}
		return new Registration.Replay(new StoredResponse(previous.status(), previous.body()));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void storeResponse(UUID actorId, UUID key, StoredResponse response) {
		var updated = jdbc.sql("""
				UPDATE shared.idempotency_key SET response_status = :status, response_body = :body
				WHERE actor_id = :actor AND key = :key
				""")
			.param("status", response.status())
			.param("body", response.body())
			.param("actor", actorId)
			.param("key", key)
			.update();
		if (updated != 1) {
			throw new IllegalStateException("storeResponse requires a key registered in this transaction");
		}
	}

	private void setLockTimeout(String value) {
		jdbc.sql("SELECT set_config('lock_timeout', :value, true)").param("value", value).query().listOfRows();
	}

	private static OffsetDateTime utc(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private record Previous(String requestHash, Integer status, String body) {
	}

}
