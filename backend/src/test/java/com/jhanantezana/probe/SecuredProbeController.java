package com.jhanantezana.probe;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.shared.CurrentActor;

@RestController
public class SecuredProbeController {

	private final CurrentActor actor;

	public SecuredProbeController(CurrentActor actor) {
		this.actor = actor;
	}

	@GetMapping("/api/v1/probe/cashier")
	@PreAuthorize("hasRole('CASHIER')")
	public Map<String, String> cashier() {
		return Map.of("id", String.valueOf(actor.id()), "role", String.valueOf(actor.role()));
	}

}
