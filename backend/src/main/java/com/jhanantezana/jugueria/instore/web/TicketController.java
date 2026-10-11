package com.jhanantezana.jugueria.instore.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.instore.internal.TicketCommands;
import com.jhanantezana.jugueria.instore.internal.TicketService;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.IdempotencyKeyHeader;
import com.jhanantezana.jugueria.shared.RequestHash;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping(TicketController.BASE)
class TicketController {

	static final String BASE = "/api/v1/tickets";

	static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private static final String FLOOR_STAFF = "hasAnyRole('SERVER', 'CASHIER', 'ADMIN')";

	private final TicketService tickets;

	private final TicketCommands commands;

	private final CurrentActor currentActor;

	TicketController(TicketService tickets, TicketCommands commands, CurrentActor currentActor) {
		this.tickets = tickets;
		this.commands = commands;
		this.currentActor = currentActor;
	}

	@GetMapping
	@PreAuthorize(FLOOR_STAFF)
	List<TicketSummaryResponse> listOpen() {
		return tickets.listOpen().stream().map(TicketSummaryResponse::from).toList();
	}

	@GetMapping("/{id}")
	@PreAuthorize(FLOOR_STAFF)
	@ApiErrors({ "instore.ticket-not-found" })
	TicketResponse get(@PathVariable UUID id) {
		return TicketResponse.from(tickets.get(id));
	}

	@PostMapping
	@PreAuthorize(FLOOR_STAFF)
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "instore.table-not-found", "instore.table-inactive", "instore.table-occupied",
			"instore.invalid-ticket", "common.idempotency-key-required", "common.idempotency-key-reused",
			"common.idempotency-in-progress" })
	ResponseEntity<TicketResponse> open(
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
			@Valid @RequestBody OpenTicketRequest request) {
		var key = IdempotencyKeyHeader.require(idempotencyKey);
		var outcome = commands.open(requireActor(), key, RequestHash.of("POST", BASE, request), request.tableId(),
				request.counterName(), request.note());
		var response = ResponseEntity.status(HttpStatus.CREATED)
			.contentType(MediaType.APPLICATION_JSON)
			.location(URI.create(BASE + "/" + outcome.ticket().id()));
		if (outcome.replayed()) {
			response.header("Idempotent-Replayed", "true");
		}
		return response.body(TicketResponse.from(outcome.ticket()));
	}

	// Authenticated by the time a method guarded by @PreAuthorize runs, so the actor is always a user here.
	private UUID requireActor() {
		var id = currentActor.id();
		if (id == null) {
			throw new IllegalStateException("A ticket command needs a signed-in user");
		}
		return id;
	}

}
