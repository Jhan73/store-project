package com.jhanantezana.probe;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.Idempotency;
import com.jhanantezana.jugueria.shared.Idempotency.Registration;
import com.jhanantezana.jugueria.shared.StoredResponse;

// The reference shape of an idempotent command: register first, run, store the response, all in one transaction.
@Service
public class IdempotentProbeService {

	public static final String REJECTED_NAME = "reject";

	// Runs after the key is registered and before the effect, so a test can hold the transaction open.
	public static volatile Runnable afterRegister = () -> {
	};

	public record Outcome(StoredResponse response, boolean replayed) {
	}

	private final Idempotency idempotency;

	private final JdbcClient jdbc;

	public IdempotentProbeService(Idempotency idempotency, JdbcClient jdbc) {
		this.idempotency = idempotency;
		this.jdbc = jdbc;
	}

	@Transactional
	public Outcome create(UUID actorId, UUID key, String requestHash, String name) {
		if (idempotency.register(actorId, key, requestHash) instanceof Registration.Replay replay) {
			return new Outcome(replay.response(), true);
		}
		afterRegister.run();
		if (REJECTED_NAME.equals(name)) {
			throw new BusinessException(CommonError.CONCURRENT_MODIFICATION, "rejected on purpose");
		}
		var id = UUID.randomUUID();
		jdbc.sql("INSERT INTO test_probe.probe_result (id, name) VALUES (:id, :name)")
			.param("id", id)
			.param("name", name)
			.update();
		var response = new StoredResponse(201, "{\"id\":\"%s\",\"name\":\"%s\"}".formatted(id, name));
		idempotency.storeResponse(actorId, key, response);
		return new Outcome(response, false);
	}

}
