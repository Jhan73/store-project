package com.jhanantezana.jugueria.identity.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.identity.internal.StaffAccountService;
import com.jhanantezana.jugueria.identity.internal.StaffProvisioningService;
import com.jhanantezana.jugueria.shared.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/staff")
class StaffController {

	private static final int MAX_PAGE_SIZE = 100;

	private final StaffProvisioningService provisioning;

	private final StaffAccountService staffAccounts;

	StaffController(StaffProvisioningService provisioning, StaffAccountService staffAccounts) {
		this.provisioning = provisioning;
		this.staffAccounts = staffAccounts;
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<StaffResponse> create(@Valid @RequestBody CreateStaffRequest request) {
		var provisioned = provisioning.createStaff(request.email(), request.role());
		var response = StaffResponse.from(provisioned.account());
		return ResponseEntity.created(URI.create("/api/v1/staff/" + provisioned.account().getId())).body(response);
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	PageResponse<StaffResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		var bounded = Math.min(size, MAX_PAGE_SIZE);
		return PageResponse.from(staffAccounts.list(PageRequest.of(page, bounded)), StaffResponse::from);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	StaffResponse get(@PathVariable UUID id) {
		return StaffResponse.from(staffAccounts.get(id));
	}

	@PatchMapping("/{id}/role")
	@PreAuthorize("hasRole('ADMIN')")
	StaffResponse changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeStaffRoleRequest request) {
		return StaffResponse.from(staffAccounts.changeRole(id, request.role()));
	}

	@PostMapping("/{id}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	StaffResponse deactivate(@PathVariable UUID id) {
		return StaffResponse.from(staffAccounts.deactivate(id));
	}

	@PostMapping("/{id}/reactivate")
	@PreAuthorize("hasRole('ADMIN')")
	StaffResponse reactivate(@PathVariable UUID id) {
		return StaffResponse.from(staffAccounts.reactivate(id));
	}

	@PostMapping("/{id}/set-password-link")
	@PreAuthorize("hasRole('ADMIN')")
	ResponseEntity<Void> resendSetPasswordLink(@PathVariable UUID id) {
		provisioning.resendSetPasswordLink(id);
		return ResponseEntity.noContent().build();
	}

}
