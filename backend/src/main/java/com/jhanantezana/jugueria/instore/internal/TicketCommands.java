package com.jhanantezana.jugueria.instore.internal;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhanantezana.jugueria.shared.Idempotency;
import com.jhanantezana.jugueria.shared.Idempotency.Registration;
import com.jhanantezana.jugueria.shared.StoredResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * The ticket commands that a retry could repeat: each registers its Idempotency-Key before doing anything else, so a
 * retry gets the first answer back instead of a second effect.
 */
@Service
public class TicketCommands {

	private static final int OK = 200;

	private static final int CREATED = 201;

	public record Outcome(TicketView ticket, boolean replayed) {
	}

	private final TicketService tickets;

	private final TicketLines lines;

	private final Idempotency idempotency;

	private final JsonMapper json;

	TicketCommands(TicketService tickets, TicketLines lines, Idempotency idempotency, JsonMapper json) {
		this.tickets = tickets;
		this.lines = lines;
		this.idempotency = idempotency;
		this.json = json;
	}

	@Transactional
	public Outcome open(UUID actorId, UUID key, String requestHash, @Nullable UUID tableId, @Nullable String counterName,
			@Nullable String note) {
		return once(actorId, key, requestHash, CREATED, () -> tickets.open(tableId, counterName, note));
	}

	@Transactional
	public Outcome addLine(UUID actorId, UUID key, String requestHash, UUID ticketId, LineDraft draft) {
		return once(actorId, key, requestHash, CREATED, () -> lines.add(ticketId, draft));
	}

	@Transactional
	public Outcome repeat(UUID actorId, UUID key, String requestHash, UUID ticketId, List<UUID> lineIds) {
		return once(actorId, key, requestHash, CREATED, () -> lines.repeat(ticketId, lineIds));
	}

	private Outcome once(UUID actorId, UUID key, String requestHash, int status, Supplier<TicketView> command) {
		if (idempotency.register(actorId, key, requestHash) instanceof Registration.Replay replay) {
			return new Outcome(json.readValue(replay.response().body(), TicketView.class), true);
		}
		var ticket = command.get();
		idempotency.storeResponse(actorId, key, new StoredResponse(status, json.writeValueAsString(ticket)));
		return new Outcome(ticket, false);
	}

}
