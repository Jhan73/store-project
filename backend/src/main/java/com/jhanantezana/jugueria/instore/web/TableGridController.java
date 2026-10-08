package com.jhanantezana.jugueria.instore.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.instore.internal.TableGridService;

@RestController
@RequestMapping("/api/v1/tables")
class TableGridController {

	private final TableGridService grid;

	TableGridController(TableGridService grid) {
		this.grid = grid;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('SERVER', 'CASHIER', 'ADMIN')")
	List<TableGridResponse> grid() {
		return grid.grid().stream().map(TableGridResponse::from).toList();
	}

}
