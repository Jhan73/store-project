package com.jhanantezana.probe;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.ErrorCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Outside the application package, so component scanning never adds these routes to a test context.
@RestController
public class ProbeController {

	enum ProbeError implements ErrorCode {

		OUT_OF_STOCK("probe.out-of-stock", HttpStatus.CONFLICT);

		private final String code;

		private final HttpStatus status;

		ProbeError(String code, HttpStatus status) {
			this.code = code;
			this.status = status;
		}

		@Override
		public String code() {
			return code;
		}

		@Override
		public HttpStatus status() {
			return status;
		}

	}

	record Line(@NotNull @Positive Integer quantity) {
	}

	record Order(@NotNull String note, @Valid List<Line> lines) {
	}

	@GetMapping("/probe/business")
	void business() {
		throw new BusinessException(ProbeError.OUT_OF_STOCK, "Product has 2 units left", Map.of("available", 2));
	}

	@PostMapping("/probe/orders")
	Order order(@Valid @RequestBody Order order) {
		return order;
	}

	@GetMapping("/probe/header")
	String header(@RequestHeader("X-Probe") String probe) {
		return probe;
	}

	@GetMapping("/probe/locked")
	void locked() {
		throw new ObjectOptimisticLockingFailureException(Order.class, "0191f2c4");
	}

	@GetMapping("/probe/quantity")
	int quantity(@RequestParam @Positive int value) {
		return value;
	}

	@GetMapping("/probe/denied")
	void denied() {
		throw new AccessDeniedException("role not allowed");
	}

	@GetMapping("/probe/boom")
	void boom() {
		throw new IllegalStateException("SELECT secret FROM credentials");
	}

}
