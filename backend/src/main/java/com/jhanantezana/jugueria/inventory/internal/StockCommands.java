package com.jhanantezana.jugueria.inventory.internal;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.shared.Idempotency;
import com.jhanantezana.jugueria.shared.Idempotency.Registration;
import com.jhanantezana.jugueria.shared.StoredResponse;

import tools.jackson.databind.json.JsonMapper;

/** The stock commands that move units on request: each registers its Idempotency-Key before doing anything else. */
@Service
public class StockCommands {

	private static final int OK = 200;

	public record Outcome(StockItemView item, boolean replayed) {
	}

	private final StockService stock;

	private final Idempotency idempotency;

	private final JsonMapper json;

	StockCommands(StockService stock, Idempotency idempotency, JsonMapper json) {
		this.stock = stock;
		this.idempotency = idempotency;
		this.json = json;
	}

	@Transactional
	public Outcome recordEntry(UUID actorId, UUID key, String requestHash, UUID productId, int quantity,
			@Nullable String note) {
		if (idempotency.register(actorId, key, requestHash) instanceof Registration.Replay replay) {
			return replayed(replay);
		}
		return stored(actorId, key, stock.recordEntry(productId, quantity, note));
	}

	@Transactional
	public Outcome adjust(UUID actorId, UUID key, String requestHash, UUID productId, int quantityDelta,
			String reasonCode, @Nullable String note) {
		if (idempotency.register(actorId, key, requestHash) instanceof Registration.Replay replay) {
			return replayed(replay);
		}
		return stored(actorId, key, stock.adjust(productId, quantityDelta, reasonCode, note));
	}

	private Outcome replayed(Registration.Replay replay) {
		return new Outcome(json.readValue(replay.response().body(), StockItemView.class), true);
	}

	private Outcome stored(UUID actorId, UUID key, StockItemView item) {
		idempotency.storeResponse(actorId, key, new StoredResponse(OK, json.writeValueAsString(item)));
		return new Outcome(item, false);
	}

}
