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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.instore.internal.PaymentCommands;
import com.jhanantezana.jugueria.instore.internal.QuickSales;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.IdempotencyKeyHeader;
import com.jhanantezana.jugueria.shared.RequestHash;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/** Counter sales paid upfront. */
@RestController
@RequestMapping(QuickSaleController.BASE)
class QuickSaleController {

	static final String BASE = "/api/v1/quick-sales";

	private static final String REGISTER_STAFF = "hasAnyRole('CASHIER', 'ADMIN')";

	private final PaymentCommands commands;

	private final QuickSales quickSales;

	private final CurrentActor currentActor;

	QuickSaleController(PaymentCommands commands, QuickSales quickSales, CurrentActor currentActor) {
		this.commands = commands;
		this.quickSales = quickSales;
		this.currentActor = currentActor;
	}

	@PostMapping
	@PreAuthorize(REGISTER_STAFF)
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "instore.no-open-shift", "instore.shift-closing", "instore.invalid-amount", "instore.invalid-payment",
			"catalog.product-not-found", "catalog.product-unavailable", "catalog.modifier-option-unavailable",
			"catalog.invalid-modifier-selection", "catalog.price-currency-mismatch", "inventory.insufficient-stock",
			"common.idempotency-key-required", "common.idempotency-key-reused", "common.idempotency-in-progress" })
	ResponseEntity<TicketResponse> sell(
			@RequestHeader(value = TicketController.IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
			@Valid @RequestBody QuickSaleRequest request) {
		var key = IdempotencyKeyHeader.require(idempotencyKey);
		var actor = currentActor.id();
		if (actor == null) {
			throw new IllegalStateException("A quick sale needs a signed-in user");
		}
		var drafts = request.lines().stream().map(LineRequest::toDraft).toList();
		var outcome = commands.quickSale(actor, key, RequestHash.of("POST", BASE, request), request.customerName(), drafts,
				request.payment().method(), request.payment().receivedAmount());
		var response = ResponseEntity.status(HttpStatus.CREATED)
			.contentType(MediaType.APPLICATION_JSON)
			.location(URI.create(TicketController.BASE + "/" + outcome.value().id()));
		if (outcome.replayed()) {
			response.header("Idempotent-Replayed", "true");
		}
		return response.body(TicketResponse.from(outcome.value()));
	}

	@GetMapping("/items")
	@PreAuthorize(REGISTER_STAFF)
	List<QuickSaleItemResponse> items() {
		return quickSales.frequentItems().stream().map(QuickSaleItemResponse::from).toList();
	}

}
