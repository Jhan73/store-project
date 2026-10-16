package com.jhanantezana.jugueria.preparation.web;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.preparation.internal.BoardCommands;
import com.jhanantezana.jugueria.preparation.internal.BoardQueries;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.CurrentActor;

/** The preparation board for the floor: SERVER, CASHIER and ADMIN all see it and operate it. */
@RestController
@RequestMapping("/api/v1/board")
class BoardController {

	private static final String STAFF = "hasAnyRole('SERVER', 'CASHIER', 'ADMIN')";

	private final BoardQueries queries;

	private final BoardCommands commands;

	private final CurrentActor currentActor;

	BoardController(BoardQueries queries, BoardCommands commands, CurrentActor currentActor) {
		this.queries = queries;
		this.commands = commands;
		this.currentActor = currentActor;
	}

	@GetMapping("/tickets")
	@PreAuthorize(STAFF)
	List<BoardOrderResponse> tickets(@RequestParam(required = false) @Nullable UUID station) {
		return queries.tickets(station).stream().map(BoardOrderResponse::from).toList();
	}

	@GetMapping("/ready")
	@PreAuthorize(STAFF)
	List<BoardOrderResponse> ready(@RequestParam(required = false) @Nullable UUID station) {
		return queries.ready(station).stream().map(BoardOrderResponse::from).toList();
	}

	@GetMapping("/all-day")
	@PreAuthorize(STAFF)
	AllDayResponse allDay(@RequestParam(required = false) @Nullable UUID station) {
		return AllDayResponse.from(queries.allDay(station));
	}

	@GetMapping("/display")
	@PreAuthorize(STAFF)
	DisplayResponse display() {
		return DisplayResponse.from(queries.display());
	}

	@PostMapping("/orders/{id}/start")
	@PreAuthorize(STAFF)
	@ApiErrors({ "preparation.order-not-found", "preparation.order-cancelled", "preparation.order-already-started" })
	BoardOrderResponse start(@PathVariable UUID id) {
		return BoardOrderResponse.from(commands.start(id));
	}

	@PostMapping("/orders/{id}/ready")
	@PreAuthorize(STAFF)
	@ApiErrors({ "preparation.order-not-found", "preparation.order-cancelled", "preparation.order-not-preparing",
			"preparation.lines-not-acknowledged", "preparation.line-flagged-unavailable" })
	BoardOrderResponse readyOrder(@PathVariable UUID id) {
		return BoardOrderResponse.from(commands.readyOrder(id));
	}

	@PostMapping("/orders/{id}/handover")
	@PreAuthorize(STAFF)
	@ApiErrors({ "preparation.order-not-found", "preparation.order-cancelled", "preparation.order-not-ready" })
	BoardOrderResponse handover(@PathVariable UUID id) {
		return BoardOrderResponse.from(commands.handover(id));
	}

	@PostMapping("/orders/{id}/recall")
	@PreAuthorize(STAFF)
	@ApiErrors({ "preparation.order-not-found", "preparation.order-cancelled", "preparation.order-not-completed",
			"preparation.recall-window-expired" })
	BoardOrderResponse recall(@PathVariable UUID id) {
		return BoardOrderResponse.from(commands.recall(id));
	}

	@PostMapping("/lines/{id}/ready")
	@PreAuthorize(STAFF)
	@ApiErrors({ "preparation.line-not-found", "preparation.order-cancelled", "preparation.order-not-preparing",
			"preparation.line-not-pending", "preparation.line-flagged-unavailable" })
	BoardOrderResponse readyLine(@PathVariable UUID id) {
		return BoardOrderResponse.from(commands.readyLine(id));
	}

	@PostMapping("/lines/{id}/flag")
	@PreAuthorize(STAFF)
	@ApiErrors({ "preparation.line-not-found", "preparation.order-cancelled", "preparation.line-not-pending",
			"preparation.line-already-flagged" })
	BoardOrderResponse flagLine(@PathVariable UUID id) {
		return BoardOrderResponse.from(commands.flagLine(id));
	}

	@PostMapping("/markers/{id}/ack")
	@PreAuthorize(STAFF)
	@ApiErrors({ "preparation.marker-not-found", "preparation.marker-already-acknowledged" })
	MarkerResponse acknowledge(@PathVariable UUID id) {
		return MarkerResponse.from(commands.acknowledge(id, requireActor()));
	}

	// Authenticated by the time a method guarded by @PreAuthorize runs, so the actor is always a user here.
	private UUID requireActor() {
		var id = currentActor.id();
		if (id == null) {
			throw new IllegalStateException("A board command needs a signed-in user");
		}
		return id;
	}

}
