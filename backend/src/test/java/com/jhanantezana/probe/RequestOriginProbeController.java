package com.jhanantezana.probe;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.shared.RequestOrigin;

@RestController
public class RequestOriginProbeController {

	private final RequestOrigin origin;

	public RequestOriginProbeController(RequestOrigin origin) {
		this.origin = origin;
	}

	@GetMapping("/api/v1/probe/request-origin")
	@PreAuthorize("hasRole('CASHIER')")
	public Map<String, String> requestOrigin() {
		return Map.of("clientIp", String.valueOf(origin.clientIp()));
	}

}
