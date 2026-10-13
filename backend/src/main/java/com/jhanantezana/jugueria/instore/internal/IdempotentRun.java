package com.jhanantezana.jugueria.instore.internal;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.jhanantezana.jugueria.shared.Idempotency;
import com.jhanantezana.jugueria.shared.Idempotency.Registration;
import com.jhanantezana.jugueria.shared.StoredResponse;

import tools.jackson.databind.json.JsonMapper;

/** Registers the Idempotency-Key first, then runs the command; callers hold the use-case transaction. */
@Component
class IdempotentRun {

	private final Idempotency idempotency;

	private final JsonMapper json;

	IdempotentRun(Idempotency idempotency, JsonMapper json) {
		this.idempotency = idempotency;
		this.json = json;
	}

	<T> Outcome<T> once(UUID actorId, UUID key, String requestHash, int status, Class<T> type, Supplier<T> command) {
		if (idempotency.register(actorId, key, requestHash) instanceof Registration.Replay replay) {
			return new Outcome<>(json.readValue(replay.response().body(), type), true);
		}
		var result = command.get();
		idempotency.storeResponse(actorId, key, new StoredResponse(status, json.writeValueAsString(result)));
		return new Outcome<>(result, false);
	}

}
