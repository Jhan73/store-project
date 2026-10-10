package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jhanantezana.jugueria.TestcontainersConfiguration;
import com.jhanantezana.probe.IdempotentProbeController;
import com.jhanantezana.probe.IdempotentProbeService;
import com.jhanantezana.testsupport.AuthenticatedAs;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator",
		"jugueria.shared.idempotency.lock-timeout=300ms" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, IdempotentProbeController.class, IdempotentProbeService.class })
class IdempotentCommandIT {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	DataSource dataSource;

	@AfterEach
	void cleanUp() {
		jdbc.sql("DELETE FROM test_probe.probe_result").update();
		jdbc.sql("DELETE FROM shared.idempotency_key").update();
	}

	@Test
	void aRetryWithTheSameKeyReturnsTheFirstResponseWithoutRunningTheCommandAgain() throws Exception {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();

		var first = post(actor, key, "mango");
		var retry = post(actor, key, "mango");

		assertThat(first).hasStatus(HttpStatus.CREATED);
		assertThat(first).headers().doesNotContainHeader("Idempotent-Replayed");
		assertThat(retry).hasStatus(HttpStatus.CREATED);
		assertThat(retry).headers().hasValue("Idempotent-Replayed", "true");
		assertThat(retry).bodyText().isEqualTo(first.getResponse().getContentAsString(StandardCharsets.UTF_8));
		assertThat(results()).isEqualTo(1);
	}

	@Test
	void theSameKeyWithADifferentBodyIsRejected() {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		post(actor, key, "mango");

		var reused = post(actor, key, "papaya");

		assertThat(reused).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(reused).bodyJson().extractingPath("$.code").isEqualTo("common.idempotency-key-reused");
		assertThat(results()).isEqualTo(1);
	}

	@Test
	void aMissingKeyIsABadRequest() {
		var result = mvc.post()
			.uri(IdempotentProbeController.PATH)
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"mango\"}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("common.idempotency-key-required");
	}

	@Test
	void aRejectedCommandDoesNotKeepItsKey() {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();

		var rejected = post(actor, key, IdempotentProbeService.REJECTED_NAME);
		var retry = post(actor, key, "mango");

		assertThat(rejected).hasStatus(HttpStatus.CONFLICT);
		assertThat(retry).hasStatus(HttpStatus.CREATED);
		assertThat(retry).headers().doesNotContainHeader("Idempotent-Replayed");
	}

	@Test
	void aDuplicateWhileTheFirstRequestIsStillRunningIsAConflict() throws SQLException {
		var actor = UUID.randomUUID();
		var key = UUID.randomUUID();
		try (var inFlight = dataSource.getConnection()) {
			inFlight.setAutoCommit(false);
			try (var insert = inFlight.prepareStatement("""
					INSERT INTO shared.idempotency_key (actor_id, key, request_hash, created_at, expires_at)
					VALUES (?, ?, ?, now(), now() + interval '1 hour')
					""")) {
				insert.setObject(1, actor);
				insert.setObject(2, key);
				insert.setString(3, "0".repeat(64));
				insert.executeUpdate();

				var duplicate = post(actor, key, "mango");

				assertThat(duplicate).hasStatus(HttpStatus.CONFLICT);
				assertThat(duplicate).bodyJson().extractingPath("$.code").isEqualTo("common.idempotency-in-progress");
			}
			finally {
				inFlight.rollback();
			}
		}
	}

	@Test
	void twoActorsMaySendTheSameKey() {
		var key = UUID.randomUUID();

		var first = post(UUID.randomUUID(), key, "mango");
		var second = post(UUID.randomUUID(), key, "papaya");

		assertThat(first).hasStatus(HttpStatus.CREATED);
		assertThat(second).hasStatus(HttpStatus.CREATED);
		assertThat(second).headers().doesNotContainHeader("Idempotent-Replayed");
		assertThat(results()).isEqualTo(2);
	}

	private MvcTestResult post(UUID actor, UUID key, String name) {
		return mvc.post()
			.uri(IdempotentProbeController.PATH)
			.with(AuthenticatedAs.user(actor, Role.ADMIN))
			.header("Idempotency-Key", key.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"%s\"}".formatted(name))
			.exchange();
	}

	private long results() {
		return jdbc.sql("SELECT count(*) FROM test_probe.probe_result").query(Long.class).single();
	}

}
