package com.jhanantezana.jugueria.identity.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.jhanantezana.jugueria.identity.internal.UserAccount;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.PageRequests;
import com.jhanantezana.jugueria.shared.PageResponse;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/staff")
class StaffController {

	private final StaffProvisioningService provisioning;

	private final StaffAccountService staffAccounts;

	StaffController(StaffProvisioningService provisioning, StaffAccountService staffAccounts) {
		this.provisioning = provisioning;
		this.staffAccounts = staffAccounts;
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "identity.email-already-registered", "identity.username-already-taken", "identity.invalid-staff-role" })
	ResponseEntity<StaffResponse> create(@Valid @RequestBody CreateStaffRequest request) {
		var provisioned = provisioning.createStaff(request.username(), request.email(), request.role());
		var response = response(provisioned.account());
		return ResponseEntity.created(URI.create("/api/v1/staff/" + provisioned.account().getId())).body(response);
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	PageResponse<StaffResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		var found = staffAccounts.list(PageRequests.of(page, size));
		var deletable = staffAccounts.deletable(found.getContent());
		return PageResponse.from(found, account -> StaffResponse.from(account, deletable.contains(account.getId())));
	}

	private StaffResponse response(UserAccount account) {
		return StaffResponse.from(account, !staffAccounts.deletable(List.of(account)).isEmpty());
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "204", description = "No Content")
	@ApiErrors({ "common.not-found", "identity.cannot-modify-own-account", "identity.staff-account-in-use",
			"identity.last-active-admin-required" })
	ResponseEntity<Void> delete(@PathVariable UUID id) {
		staffAccounts.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "common.not-found" })
	StaffResponse get(@PathVariable UUID id) {
		return response(staffAccounts.get(id));
	}

	@PatchMapping("/{id}/role")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "common.not-found", "identity.invalid-staff-role", "identity.cannot-modify-own-account", "identity.last-active-admin-required", "instore.user-has-open-shift" })
	StaffResponse changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeStaffRoleRequest request) {
		return response(staffAccounts.changeRole(id, request.role()));
	}

	@PatchMapping("/{id}/username")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "common.not-found", "identity.username-already-taken" })
	StaffResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameStaffRequest request) {
		return response(staffAccounts.rename(id, request.username()));
	}

	@PostMapping("/{id}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "common.not-found", "identity.cannot-modify-own-account", "identity.last-active-admin-required", "instore.user-has-open-shift" })
	StaffResponse deactivate(@PathVariable UUID id) {
		return response(staffAccounts.deactivate(id));
	}

	@PostMapping("/{id}/reactivate")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "common.not-found", "identity.username-already-taken" })
	StaffResponse reactivate(@PathVariable UUID id) {
		return response(staffAccounts.reactivate(id));
	}

	@PostMapping("/{id}/set-password-link")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "204", description = "No Content")
	@ApiErrors({ "common.not-found", "identity.cannot-modify-own-account" })
	ResponseEntity<Void> resendSetPasswordLink(@PathVariable UUID id) {
		provisioning.resendSetPasswordLink(id);
		return ResponseEntity.noContent().build();
	}

}
