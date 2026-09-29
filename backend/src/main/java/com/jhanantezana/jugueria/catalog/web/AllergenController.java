package com.jhanantezana.jugueria.catalog.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.catalog.Allergen;

@RestController
@RequestMapping("/api/v1/admin/allergens")
class AllergenController {

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	List<Allergen> list() {
		return List.of(Allergen.values());
	}

}
