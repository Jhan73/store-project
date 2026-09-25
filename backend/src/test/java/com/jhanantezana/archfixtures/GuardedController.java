package com.jhanantezana.archfixtures;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.security.PermitAll;

@RestController
public class GuardedController {

	@GetMapping("/menu")
	@PermitAll
	public String menu() {
		return "menu";
	}

	@PostMapping("/orders")
	@PreAuthorize("hasRole('CUSTOMER')")
	public void order() {
	}

}
