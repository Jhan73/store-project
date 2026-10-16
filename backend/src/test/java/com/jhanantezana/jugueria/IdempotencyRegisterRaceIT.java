package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.jhanantezana.jugueria.shared.Idempotency;
import com.jhanantezana.jugueria.shared.Idempotency.Registration;
import com.jhanantezana.jugueria.shared.RequestHash;

// A statement trigger deletes the conflicting row right after the insert reports a conflict, which is exactly
// what a cleanup sweep committing between the insert and the follow-up read does.
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class IdempotencyRegisterRaceIT {

	private static final String VICTIM_HASH = "9".repeat(64);

	@Autowired
	Idempotency idempotency;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	JdbcClient jdbc;

	// Installed after the expired row exists, or the trigger would delete it on the test's own insert.
	private static void installTrigger() throws SQLException {
		migrate("""
				CREATE FUNCTION shared.delete_victim() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN
					DELETE FROM shared.idempotency_key WHERE request_hash = '%s';
					RETURN NULL;
				END $$
				""".formatted(VICTIM_HASH), """
				CREATE TRIGGER delete_victim AFTER INSERT ON shared.idempotency_key
				FOR EACH STATEMENT EXECUTE FUNCTION shared.delete_victim()
				""");
	}

	@AfterEach
	void removeTrigger() throws SQLException {
		migrate("DROP TRIGGER IF EXISTS delete_victim ON shared.idempotency_key",
				"DROP FUNCTION IF EXISTS shared.delete_victim()");
		jdbc.sql("DELETE FROM shared.idempotency_key").update();
	}

	@Test
	void anExpiredKeyDeletedBetweenTheConflictAndTheReadIsRegisteredAgain() throws SQLException {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO shared.idempotency_key (actor_id, key, request_hash, response_status, response_body,
					created_at, expires_at)
				VALUES (:actor, :key, :hash, 201, '{}', now() - interval '2 days', now() - interval '1 day')
				""").param("actor", actor).param("key", key).param("hash", VICTIM_HASH).update();
		installTrigger();

		var result = tx.execute(status -> idempotency.register(actor, key, RequestHash.of("POST", "/x", null)));

		assertThat(result).isEqualTo(new Registration.Fresh());
	}

	private static void migrate(String... statements) throws SQLException {
		try (var connection = DriverManager.getConnection(TestcontainersConfiguration.POSTGRES.getJdbcUrl(),
				"migrator", "migrator"); var statement = connection.createStatement()) {
			for (var sql : statements) {
				statement.execute(sql);
			}
		}
	}

}
