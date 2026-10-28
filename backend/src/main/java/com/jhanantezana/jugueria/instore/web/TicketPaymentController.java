package com.jhanantezana.jugueria.instore.web;

import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.instore.internal.PaymentCommands;
import com.jhanantezana.jugueria.instore.internal.TicketPayments;
import com.jhanantezana.jugueria.instore.internal.TicketSaleVoids;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.IdempotencyKeyHeader;
import com.jhanantezana.jugueria.shared.RequestHash;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/** Payments of in-store tickets: taking them, voiding them and splitting the bill. */
@RestController
@RequestMapping(TicketController.BASE)
class TicketPaymentController {

	private static final String REGISTER_STAFF = "hasAnyRole('CASHIER', 'ADMIN')";

	private final PaymentCommands commands;

	private final TicketPayments payments;

	private final TicketSaleVoids saleVoids;

	private final CurrentActor currentActor;

	TicketPaymentController(PaymentCommands commands, TicketPayments payments, TicketSaleVoids saleVoids,
			CurrentActor currentActor) {
		this.commands = commands;
		this.payments = payments;
		this.saleVoids = saleVoids;
		this.currentActor = currentActor;
	}

	@PostMapping("/{id}/payments")
	@PreAuthorize(REGISTER_STAFF)
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed", "instore.no-open-shift", "instore.shift-closing",
			"instore.invalid-amount", "instore.invalid-payment", "instore.payment-exceeds-balance",
			"instore.ticket-has-unsent-lines", "instore.line-not-found", "instore.line-already-paid",
			"instore.wallet-provider-required", "instore.wallet-provider-not-allowed",
			"instore.wallet-provider-not-found", "instore.wallet-provider-inactive",
			"inventory.insufficient-stock", "common.idempotency-key-required", "common.idempotency-key-reused",
			"common.idempotency-in-progress" })
	ResponseEntity<TicketResponse> record(@PathVariable UUID id,
			@RequestHeader(value = TicketController.IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
			@Valid @RequestBody RecordPaymentRequest request) {
		var key = IdempotencyKeyHeader.require(idempotencyKey);
		var path = TicketController.BASE + "/" + id + "/payments";
		var outcome = commands.record(requireActor(), key, RequestHash.of("POST", path, request), id, request.method(),
				request.amount(), request.receivedAmount(), request.lineIds(), request.walletProviderId());
		var response = ResponseEntity.status(HttpStatus.CREATED)
			.contentType(MediaType.APPLICATION_JSON)
			.location(URI.create(TicketController.BASE + "/" + id));
		if (outcome.replayed()) {
			response.header("Idempotent-Replayed", "true");
		}
		return response.body(TicketResponse.from(outcome.value()));
	}

	@PostMapping("/{id}/payments/{paymentId}/void")
	@PreAuthorize(REGISTER_STAFF)
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed", "instore.payment-not-found",
			"instore.invalid-reason", "instore.no-open-shift", "instore.shift-closing" })
	TicketResponse voidPayment(@PathVariable UUID id, @PathVariable UUID paymentId,
			@Valid @RequestBody VoidPaymentRequest request) {
		return TicketResponse.from(payments.voidPayment(id, paymentId, request.reason(), request.note()));
	}

	@PostMapping("/{id}/void")
	@PreAuthorize(REGISTER_STAFF)
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-not-closed", "instore.invalid-reason",
			"instore.no-open-shift", "instore.shift-closing", "instore.sale-void-not-permitted" })
	TicketResponse voidSale(@PathVariable UUID id, @Valid @RequestBody VoidSaleRequest request) {
		return TicketResponse.from(saleVoids.voidSale(id, request.reason(), request.note()));
	}

	@GetMapping("/{id}/split")
	@PreAuthorize(REGISTER_STAFF)
	@ApiErrors({ "instore.ticket-not-found", "instore.ticket-closed", "instore.invalid-payment" })
	SplitResponse split(@PathVariable UUID id, @RequestParam int parts) {
		return new SplitResponse(payments.split(id, parts));
	}

	// Authenticated by the time a method guarded by @PreAuthorize runs, so the actor is always a user here.
	private UUID requireActor() {
		var actor = currentActor.id();
		if (actor == null) {
			throw new IllegalStateException("A payment needs a signed-in user");
		}
		return actor;
	}

}
