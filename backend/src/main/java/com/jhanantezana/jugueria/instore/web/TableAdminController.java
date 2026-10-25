package com.jhanantezana.jugueria.instore.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.instore.internal.DiningTable;
import com.jhanantezana.jugueria.instore.internal.DiningTableService;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.IfMatchHeader;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/tables")
class TableAdminController {

	private final DiningTableService tables;

	TableAdminController(DiningTableService tables) {
		this.tables = tables;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	List<TableResponse> list() {
		var all = tables.list();
		var deletable = tables.deletable(all.stream().map(DiningTable::getId).toList());
		return all.stream().map(table -> TableResponse.from(table, deletable.contains(table.getId()))).toList();
	}

	private TableResponse response(DiningTable table) {
		return TableResponse.from(table, !tables.deletable(List.of(table.getId())).isEmpty());
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "204", description = "No Content")
	@ApiErrors({ "instore.table-not-found", "instore.table-in-use" })
	ResponseEntity<Void> delete(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		tables.delete(id, IfMatchHeader.require(ifMatch));
		return ResponseEntity.noContent().build();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "instore.table-name-already-used" })
	ResponseEntity<TableResponse> create(@Valid @RequestBody CreateTableRequest request) {
		var table = tables.create(request.name(), request.area(), request.displayOrderOrDefault());
		return ResponseEntity.created(URI.create("/api/v1/admin/tables/" + table.getId()))
			.eTag(ETags.format(table.getVersion()))
			.body(response(table));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "instore.table-not-found", "instore.table-name-already-used" })
	ResponseEntity<TableResponse> change(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@Valid @RequestBody UpdateTableRequest request) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		var table = tables.change(id, request.name(), request.area(), request.displayOrder(), expectedVersion);
		return ResponseEntity.ok().eTag(ETags.format(table.getVersion())).body(response(table));
	}

	@PostMapping("/{id}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "instore.table-not-found" })
	ResponseEntity<TableResponse> deactivate(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var table = tables.deactivate(id, IfMatchHeader.require(ifMatch));
		return ResponseEntity.ok().eTag(ETags.format(table.getVersion())).body(response(table));
	}

	@PostMapping("/{id}/reactivate")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "instore.table-not-found" })
	ResponseEntity<TableResponse> reactivate(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var table = tables.reactivate(id, IfMatchHeader.require(ifMatch));
		return ResponseEntity.ok().eTag(ETags.format(table.getVersion())).body(response(table));
	}

}
