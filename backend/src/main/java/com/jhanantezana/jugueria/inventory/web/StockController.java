package com.jhanantezana.jugueria.inventory.web;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.inventory.internal.StockCommands;
import com.jhanantezana.jugueria.inventory.internal.StockService;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.IdempotencyKeyHeader;
import com.jhanantezana.jugueria.shared.PageRequests;
import com.jhanantezana.jugueria.shared.PageResponse;
import com.jhanantezana.jugueria.shared.RequestHash;

import jakarta.validation.Valid;

@RestController
@RequestMapping(StockController.BASE)
class StockController {

	static final String BASE = "/api/v1/admin/stock-items";

	private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private final StockService stock;

	private final StockCommands commands;

	private final CurrentActor currentActor;

	StockController(StockService stock, StockCommands commands, CurrentActor currentActor) {
		this.stock = stock;
		this.commands = commands;
		this.currentActor = currentActor;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	List<StockItemResponse> list() {
		return stock.list().stream().map(StockItemResponse::from).toList();
	}

	@GetMapping("/low")
	@PreAuthorize("hasRole('ADMIN')")
	List<StockItemResponse> lowStock() {
		return stock.lowStock().stream().map(StockItemResponse::from).toList();
	}

	@GetMapping("/{productId}")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "inventory.stock-item-not-found" })
	StockItemResponse get(@PathVariable UUID productId) {
		return StockItemResponse.from(stock.get(productId));
	}

	@PutMapping("/{productId}")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "inventory.product-not-found", "inventory.stock-reserved" })
	StockItemResponse track(@PathVariable UUID productId, @Valid @RequestBody TrackStockRequest request) {
		return StockItemResponse.from(stock.track(productId, request.tracked(), request.lowStockThreshold()));
	}

	@PostMapping("/{productId}/entries")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "inventory.stock-item-not-found", "inventory.product-not-tracked", "common.idempotency-key-required",
			"common.idempotency-key-reused", "common.idempotency-in-progress" })
	ResponseEntity<StockItemResponse> recordEntry(@PathVariable UUID productId,
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
			@Valid @RequestBody RecordEntryRequest request) {
		var key = IdempotencyKeyHeader.require(idempotencyKey);
		var path = BASE + "/" + productId + "/entries";
		var outcome = commands.recordEntry(requireActor(), key, RequestHash.of("POST", path, request), productId,
				request.quantity(), request.note());
		return respond(outcome);
	}

	@PostMapping("/{productId}/adjustments")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "inventory.stock-item-not-found", "inventory.product-not-tracked", "inventory.insufficient-stock",
			"inventory.invalid-quantity", "inventory.invalid-reason", "common.idempotency-key-required",
			"common.idempotency-key-reused", "common.idempotency-in-progress" })
	ResponseEntity<StockItemResponse> adjust(@PathVariable UUID productId,
			@RequestHeader(value = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
			@Valid @RequestBody AdjustStockRequest request) {
		var key = IdempotencyKeyHeader.require(idempotencyKey);
		var path = BASE + "/" + productId + "/adjustments";
		var outcome = commands.adjust(requireActor(), key, RequestHash.of("POST", path, request), productId,
				request.quantityDelta(), request.reason(), request.note());
		return respond(outcome);
	}

	@GetMapping("/{productId}/movements")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "inventory.stock-item-not-found" })
	PageResponse<MovementResponse> movements(@PathVariable UUID productId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(stock.movements(productId, PageRequests.of(page, size)), MovementResponse::from);
	}

	// Authenticated by the time a method guarded by @PreAuthorize runs, so the actor is always a user here.
	private UUID requireActor() {
		var id = currentActor.id();
		if (id == null) {
			throw new IllegalStateException("A stock command needs a signed-in user");
		}
		return id;
	}

	private static ResponseEntity<StockItemResponse> respond(StockCommands.Outcome outcome) {
		var response = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
		if (outcome.replayed()) {
			response.header("Idempotent-Replayed", "true");
		}
		return response.body(StockItemResponse.from(outcome.item()));
	}

}
