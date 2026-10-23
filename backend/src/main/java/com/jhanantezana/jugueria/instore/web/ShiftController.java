package com.jhanantezana.jugueria.instore.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.instore.internal.CashMovements;
import com.jhanantezana.jugueria.instore.internal.ShiftClosing;
import com.jhanantezana.jugueria.instore.internal.ShiftCommands;
import com.jhanantezana.jugueria.instore.internal.ShiftService;
import com.jhanantezana.jugueria.instore.internal.ShiftStatus;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.IdempotencyKeyHeader;
import com.jhanantezana.jugueria.shared.PageRequests;
import com.jhanantezana.jugueria.shared.PageResponse;
import com.jhanantezana.jugueria.shared.RequestHash;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/**
 * Register shifts. While one is open nothing here returns expected cash: the close takes the count first and answers
 * with the expected cash, the difference and the totals.
 */
@RestController
@RequestMapping(ShiftController.BASE)
class ShiftController {

	static final String BASE = "/api/v1/shifts";

	private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private static final String REGISTER_STAFF = "hasAnyRole('CASHIER', 'ADMIN')";

	private final ShiftService shifts;

	private final ShiftClosing closing;

	private final CashMovements movements;

	private final ShiftCommands commands;

	private final CurrentActor currentActor;

	ShiftController(ShiftService shifts, ShiftClosing closing, CashMovements movements, ShiftCommands commands,
			CurrentActor currentActor) {
		this.shifts = shifts;
		this.closing = closing;
		this.movements = movements;
		this.commands = commands;
		this.currentActor = currentActor;
	}

	@PostMapping
	@PreAuthorize(REGISTER_STAFF)
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "instore.shift-already-open", "instore.invalid-amount", "common.idempotency-key-required",
			"common.idempotency-key-reused", "common.idempotency-in-progress" })
	ResponseEntity<ShiftResponse> open(
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
			@Valid @RequestBody OpenShiftRequest request) {
		var key = IdempotencyKeyHeader.require(idempotencyKey);
		var outcome = commands.open(requireActor(), key, RequestHash.of("POST", BASE, request), request.openingFloat());
		var response = ResponseEntity.status(HttpStatus.CREATED)
			.contentType(MediaType.APPLICATION_JSON)
			.location(URI.create(BASE + "/" + outcome.value().id()));
		if (outcome.replayed()) {
			response.header("Idempotent-Replayed", "true");
		}
		return response.body(ShiftResponse.from(outcome.value()));
	}

	@GetMapping("/current")
	@PreAuthorize(REGISTER_STAFF)
	@ApiErrors({ "instore.shift-not-found" })
	ShiftResponse current() {
		return ShiftResponse.from(shifts.current());
	}

	@GetMapping("/{id}")
	@PreAuthorize(REGISTER_STAFF)
	@ApiErrors({ "instore.shift-not-found" })
	ShiftResponse get(@PathVariable UUID id) {
		return ShiftResponse.from(shifts.get(id));
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "instore.invalid-date-range" })
	PageResponse<ShiftResponse> list(@RequestParam(required = false) @Nullable ShiftStatus status,
			@RequestParam(required = false) @Nullable UUID cashierId,
			@RequestParam(required = false) @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(shifts.list(status, cashierId, from, to, PageRequests.of(page, size)),
				ShiftResponse::from);
	}

	@PostMapping("/{id}/close")
	@PreAuthorize(REGISTER_STAFF)
	@ApiErrors({ "instore.shift-not-found", "instore.shift-closed", "instore.invalid-amount",
			"instore.counted-cash-locked", "instore.difference-note-required" })
	ShiftResponse close(@PathVariable UUID id, @Valid @RequestBody CloseShiftRequest request) {
		return ShiftResponse.from(closing.close(id, request.countedCash(), request.note()));
	}

	@PostMapping("/{id}/force-close")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "instore.shift-not-found", "instore.shift-closed", "instore.invalid-reason" })
	ShiftResponse forceClose(@PathVariable UUID id, @Valid @RequestBody ForceCloseShiftRequest request) {
		return ShiftResponse.from(closing.forceClose(id, request.reason()));
	}

	@PostMapping("/{id}/cash-movements")
	@PreAuthorize(REGISTER_STAFF)
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "instore.shift-not-found", "instore.shift-closed", "instore.shift-closing", "instore.invalid-amount",
			"instore.invalid-reason", "instore.invalid-cash-movement", "instore.cash-out-exceeds-drawer",
			"common.idempotency-key-required", "common.idempotency-key-reused", "common.idempotency-in-progress" })
	ResponseEntity<CashMovementResponse> recordMovement(@PathVariable UUID id,
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
			@Valid @RequestBody CashMovementRequest request) {
		var key = IdempotencyKeyHeader.require(idempotencyKey);
		var path = BASE + "/" + id + "/cash-movements";
		var outcome = commands.recordMovement(requireActor(), key, RequestHash.of("POST", path, request), id,
				request.type(), request.amount(), request.reasonCode(), request.note(), request.reference());
		var response = ResponseEntity.status(HttpStatus.CREATED)
			.contentType(MediaType.APPLICATION_JSON)
			.location(URI.create(path + "/" + outcome.value().id()));
		if (outcome.replayed()) {
			response.header("Idempotent-Replayed", "true");
		}
		return response.body(CashMovementResponse.from(outcome.value()));
	}

	@GetMapping("/{id}/cash-movements")
	@PreAuthorize(REGISTER_STAFF)
	@ApiErrors({ "instore.shift-not-found" })
	List<CashMovementResponse> listMovements(@PathVariable UUID id) {
		return movements.list(id).stream().map(CashMovementResponse::from).toList();
	}

	@GetMapping("/{id}/cash-movements/{movementId}")
	@PreAuthorize(REGISTER_STAFF)
	@ApiErrors({ "instore.shift-not-found", "instore.cash-movement-not-found" })
	CashMovementResponse getMovement(@PathVariable UUID id, @PathVariable UUID movementId) {
		return CashMovementResponse.from(movements.get(id, movementId));
	}

	// Authenticated by the time a method guarded by @PreAuthorize runs, so the actor is always a user here.
	private UUID requireActor() {
		var id = currentActor.id();
		if (id == null) {
			throw new IllegalStateException("A shift command needs a signed-in user");
		}
		return id;
	}

}
