package com.jhanantezana.jugueria.shared;

import java.util.UUID;

/**
 * Makes a command safe to retry. Both methods must run inside the use-case transaction (the first statement of
 * the service method for {@link #register}), so a rejected command rolls its key back and a crash cannot leave a
 * result without its key.
 */
public interface Idempotency {

	/**
	 * Registers the key for the actor. A concurrent duplicate waits for the first transaction to end, then replays
	 * its response.
	 *
	 * @throws BusinessException {@link CommonError#IDEMPOTENCY_KEY_REUSED} when the key was used for a different
	 * request, {@link CommonError#IDEMPOTENCY_IN_PROGRESS} when the first request is still running past the lock
	 * timeout
	 */
	Registration register(UUID actorId, UUID key, String requestHash);

	/** Stores the response of a {@link Registration.Fresh} registration, in the same transaction. */
	void storeResponse(UUID actorId, UUID key, StoredResponse response);

	sealed interface Registration {

		/** First time this key is seen: run the command, then store its response. */
		record Fresh() implements Registration {
		}

		/** The key was already used for this request: return the stored response with {@code Idempotent-Replayed: true}. */
		record Replay(StoredResponse response) implements Registration {
		}

	}

}
