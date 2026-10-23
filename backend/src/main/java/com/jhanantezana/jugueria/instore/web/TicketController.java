package com.jhanantezana.jugueria.instore.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.instore.TicketType;
import com.jhanantezana.jugueria.instore.internal.ClosedTicketFilter;
import com.jhanantezana.jugueria.instore.internal.TicketCancellation;
import com.jhanantezana.jugueria.instore.internal.TicketCommands;
import com.jhanantezana.jugueria.instore.internal.TicketLines;
import com.jhanantezana.jugueria.instore.internal.TicketPlacement;
import com.jhanantezana.jugueria.instore.internal.TicketSending;
import com.jhanantezana.jugueria.instore.internal.TicketService;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.IdempotencyKeyHeader;
import com.jhanantezana.jugueria.shared.PageRequests;
import com.jhanantezana.jugueria.shared.PageResponse;
import com.jhanantezana.jugueria.shared.RequestHash;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping(TicketController.BASE)
class TicketController {

	static final String BASE = "/api/v1/tickets";

	static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private static final String FLOOR_STAFF = "hasAnyRole('SERVER', 'CASHIER', 'ADMIN')";

	private static final String REGISTER_STAFF = "hasAnyRole('CASHIER', 'ADMIN')";

	private final TicketService tickets;

	private final TicketCommands commands;

	private final TicketLines lines;

	private final TicketSending sending;

	private final TicketPlacement placement;

	private final TicketCancellation cancellation;

	private final CurrentActor currentActor;

	TicketController(TicketService tickets, TicketCommands commands, TicketLines lines, TicketSending sending,
			TicketPlacement placement, TicketCancellation cancellation, CurrentActor currentActor) {
		this.tickets = tickets;
		this.lines = lines;
		this.sending = sending;
		this.placement = placement;
		this.cancellation = cancellation;
		this.commands = commands;
		this.currentActor = currentActor;
	}

	@GetMapping
	@PreAuthorize(FLOOR_STAFF)
	PageResponse<TicketSummaryResponse> listOpen(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + PageRequests.MAX_PAGE_SIZE) int size) {
		return PageResponse.from(tickets.listOpen(PageRequests.of(page, size)), TicketSummaryResponse::from);
	}

	@GetMapping("/closed")
	@PreAuthorize(REGISTER_STAFF)
	@ApiErrors({ "instore.invalid-date-range" })
	PageResponse<ClosedTicketSummaryResponse> listClosed(
			@RequestParam(required = false) @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) @Nullable UUID cashierId,
			@RequestParam(required = false) @Nullable UUID tableId,
			@RequestParam(required = false) @Nullable TicketType type, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		var filter = new ClosedTicketFilter(from, to, cashierId, tableId, type);
		return PageResponse.from(tickets.listClosed(filter, PageRequests.of(page, size)),
				ClosedTicketSummaryResponse::from);
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
		return created(outcome);
	}

	@PostMapping("/{id}/lines")
	@PreAuthorize(FLOOR_STAFF)
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed", "instore.too-many-lines", "catalog.product-not-found",
			"catalog.product-unavailable", "catalog.modifier-option-unavailable", "catalog.invalid-modifier-selection",
			"catalog.price-currency-mismatch", "common.idempotency-key-required", "common.idempotency-key-reused",
			"common.idempotency-in-progress" })
	ResponseEntity<TicketResponse> addLine(@PathVariable UUID id,
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
			@Valid @RequestBody LineRequest request) {
		var key = IdempotencyKeyHeader.require(idempotencyKey);
		var path = BASE + "/" + id + "/lines";
		var outcome = commands.addLine(requireActor(), key, RequestHash.of("POST", path, request), id, request.toDraft());
		return created(outcome);
	}

	@PutMapping("/{id}/lines/{lineId}")
	@PreAuthorize(FLOOR_STAFF)
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed", "instore.line-not-found",
			"instore.line-already-sent", "catalog.product-not-found", "catalog.product-unavailable",
			"catalog.modifier-option-unavailable", "catalog.invalid-modifier-selection", "catalog.price-currency-mismatch" })
	TicketResponse changeLine(@PathVariable UUID id, @PathVariable UUID lineId, @Valid @RequestBody LineRequest request) {
		return TicketResponse.from(lines.change(id, lineId, request.toDraft()));
	}

	@DeleteMapping("/{id}/lines/{lineId}")
	@PreAuthorize(FLOOR_STAFF)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed", "instore.line-not-found",
			"instore.line-already-sent" })
	void deleteLine(@PathVariable UUID id, @PathVariable UUID lineId) {
		lines.delete(id, lineId);
	}

	@PostMapping("/{id}/repeat")
	@PreAuthorize(FLOOR_STAFF)
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed", "instore.line-not-found", "instore.too-many-lines",
			"catalog.product-not-found", "catalog.product-unavailable", "catalog.modifier-option-unavailable",
			"catalog.invalid-modifier-selection", "catalog.price-currency-mismatch", "common.idempotency-key-required",
			"common.idempotency-key-reused", "common.idempotency-in-progress" })
	ResponseEntity<TicketResponse> repeat(@PathVariable UUID id,
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
			@Valid @RequestBody RepeatLinesRequest request) {
		var key = IdempotencyKeyHeader.require(idempotencyKey);
		var path = BASE + "/" + id + "/repeat";
		var outcome = commands.repeat(requireActor(), key, RequestHash.of("POST", path, request), id, request.lineIds());
		return created(outcome);
	}

	@PostMapping("/{id}/lines/{lineId}/void")
	@PreAuthorize(FLOOR_STAFF)
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed", "instore.line-not-found", "instore.line-not-sent",
			"instore.invalid-reason", "preparation.line-already-ready", "preparation.order-closed",
			"common.idempotency-key-required", "common.idempotency-key-reused", "common.idempotency-in-progress" })
	ResponseEntity<TicketResponse> voidLine(@PathVariable UUID id, @PathVariable UUID lineId,
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
			@Valid @RequestBody VoidLineRequest request) {
		var key = IdempotencyKeyHeader.require(idempotencyKey);
		var path = BASE + "/" + id + "/lines/" + lineId + "/void";
		var outcome = commands.voidLine(requireActor(), key, RequestHash.of("POST", path, request), id, lineId,
				request.reason(), request.note());
		var response = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
		if (outcome.replayed()) {
			response.header("Idempotent-Replayed", "true");
		}
		return response.body(TicketResponse.from(outcome.ticket()));
	}

	@PostMapping("/{id}/send")
	@PreAuthorize(FLOOR_STAFF)
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed", "instore.line-product-unavailable" })
	TicketResponse send(@PathVariable UUID id) {
		return TicketResponse.from(sending.send(id));
	}

	@PostMapping("/{id}/transfer")
	@PreAuthorize(FLOOR_STAFF)
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed", "instore.ticket-has-no-table",
			"instore.table-not-found", "instore.table-inactive", "instore.table-occupied" })
	TicketResponse transfer(@PathVariable UUID id, @Valid @RequestBody TransferTicketRequest request) {
		return TicketResponse.from(placement.transfer(id, request.tableId()));
	}

	@PutMapping("/{id}/bill-requested")
	@PreAuthorize(FLOOR_STAFF)
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed" })
	TicketResponse billRequested(@PathVariable UUID id, @Valid @RequestBody BillRequestedRequest request) {
		return TicketResponse.from(placement.setBillRequested(id, request.requested()));
	}

	@PutMapping("/{id}/note")
	@PreAuthorize(FLOOR_STAFF)
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed" })
	TicketResponse changeNote(@PathVariable UUID id, @Valid @RequestBody ChangeNoteRequest request) {
		return TicketResponse.from(tickets.changeNote(id, request.note()));
	}

	@PostMapping("/{id}/cancel")
	@PreAuthorize(FLOOR_STAFF)
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed", "instore.ticket-not-empty" })
	TicketResponse cancel(@PathVariable UUID id) {
		return TicketResponse.from(cancellation.cancel(id));
	}

	private static ResponseEntity<TicketResponse> created(TicketCommands.Outcome outcome) {
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
