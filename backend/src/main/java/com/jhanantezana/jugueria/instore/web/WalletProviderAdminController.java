package com.jhanantezana.jugueria.instore.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.instore.internal.WalletProviderService;
import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.ETags;
import com.jhanantezana.jugueria.shared.IfMatchHeader;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/wallet-providers")
class WalletProviderAdminController {

	private final WalletProviderService providers;

	WalletProviderAdminController(WalletProviderService providers) {
		this.providers = providers;
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	List<WalletProviderResponse> list() {
		return providers.list().stream().map(WalletProviderResponse::from).toList();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiErrors({ "instore.wallet-provider-name-already-used" })
	ResponseEntity<WalletProviderResponse> create(@Valid @RequestBody SaveWalletProviderRequest request) {
		var provider = providers.create(request.name());
		return ResponseEntity.created(URI.create("/api/v1/admin/wallet-providers/" + provider.getId()))
			.eTag(ETags.format(provider.getVersion()))
			.body(WalletProviderResponse.from(provider));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "instore.wallet-provider-not-found", "instore.wallet-provider-name-already-used" })
	ResponseEntity<WalletProviderResponse> rename(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch,
			@Valid @RequestBody SaveWalletProviderRequest request) {
		var provider = providers.rename(id, request.name(), IfMatchHeader.require(ifMatch));
		return ResponseEntity.ok().eTag(ETags.format(provider.getVersion())).body(WalletProviderResponse.from(provider));
	}

	@PostMapping("/{id}/deactivate")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "instore.wallet-provider-not-found" })
	ResponseEntity<WalletProviderResponse> deactivate(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var provider = providers.deactivate(id, IfMatchHeader.require(ifMatch));
		return ResponseEntity.ok().eTag(ETags.format(provider.getVersion())).body(WalletProviderResponse.from(provider));
	}

	@PostMapping("/{id}/reactivate")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiErrors({ "instore.wallet-provider-not-found", "instore.wallet-provider-name-already-used" })
	ResponseEntity<WalletProviderResponse> reactivate(@PathVariable UUID id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Nullable String ifMatch) {
		var provider = providers.reactivate(id, IfMatchHeader.require(ifMatch));
		return ResponseEntity.ok().eTag(ETags.format(provider.getVersion())).body(WalletProviderResponse.from(provider));
	}

}
