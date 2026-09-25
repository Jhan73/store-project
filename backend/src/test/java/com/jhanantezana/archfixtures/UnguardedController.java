package com.jhanantezana.archfixtures;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UnguardedController {

	@GetMapping("/things")
	public List<String> list() {
		return List.of();
	}

	@PostMapping("/things")
	@PreAuthorize("hasRole('ADMIN')")
	public void create() {
	}

	public void helper() {
	}

}
