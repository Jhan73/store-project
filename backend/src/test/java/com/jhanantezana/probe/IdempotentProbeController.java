package com.jhanantezana.probe;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.shared.CurrentActor;
import com.jhanantezana.jugueria.shared.IdempotencyKeyHeader;
import com.jhanantezana.jugueria.shared.RequestHash;

@RestController
public class IdempotentProbeController {

	public static final String PATH = "/api/v1/probe/idempotent-results";

	public record Request(String name) {
	}

	private final IdempotentProbeService service;

	private final CurrentActor actor;

	public IdempotentProbeController(IdempotentProbeService service, CurrentActor actor) {
		this.service = service;
		this.actor = actor;
	}

	@PostMapping(PATH)
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<String> create(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody Request request) {
		var key = IdempotencyKeyHeader.require(idempotencyKey);

		var outcome = service.create(actor.id(), key, RequestHash.of("POST", PATH, request), request.name());

		var response = ResponseEntity.status(outcome.response().status()).contentType(MediaType.APPLICATION_JSON);
		if (outcome.replayed()) {
			response.header("Idempotent-Replayed", "true");
		}
		return response.body(outcome.response().body());
	}

}
