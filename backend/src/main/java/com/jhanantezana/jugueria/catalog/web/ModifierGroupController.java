package com.jhanantezana.jugueria.catalog.web;

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

import com.jhanantezana.jugueria.catalog.internal.ModifierGroupService;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.IfMatchHeader;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/modifier-groups")
class ModifierGroupController {

	private final ModifierGroupService groups;

	ModifierGroupController(ModifierGroupService groups) {
		this.groups = groups;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	List<ModifierGroupResponse> list() {
		return groups.list().stream().map(ModifierGroupResponse::from).toList();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<ModifierGroupResponse> get(@PathVariable UUID id) {
		var group = groups.get(id);
		return ResponseEntity.ok().eTag(ETags.format(group.getVersion())).body(ModifierGroupResponse.from(group));
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<ModifierGroupResponse> create(@Valid @RequestBody SaveModifierGroupRequest request) {
		var group = groups.create(request.name(), request.required(), request.minChoices(), request.maxChoices(),
				request.optionDefinitions());
		return ResponseEntity.created(URI.create("/api/v1/admin/modifier-groups/" + group.getId()))
			.eTag(ETags.format(group.getVersion()))
			.body(ModifierGroupResponse.from(group));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<ModifierGroupResponse> change(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@Valid @RequestBody SaveModifierGroupRequest request) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		var group = groups.change(id, request.name(), request.required(), request.minChoices(), request.maxChoices(),
				request.optionDefinitions(), expectedVersion);
		return ResponseEntity.ok().eTag(ETags.format(group.getVersion())).body(ModifierGroupResponse.from(group));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<Void> delete(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var expectedVersion = IfMatchHeader.require(ifMatch);
		groups.delete(id, expectedVersion);
		return ResponseEntity.noContent().build();
	}

}
