package com.jhanantezana.jugueria.instore.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.instore.internal.WalletProviderService;

/** The active wallet providers a cashier picks from when taking a wallet payment. */
@RestController
@RequestMapping("/api/v1/wallet-providers")
class WalletProviderController {

	private final WalletProviderService providers;

	WalletProviderController(WalletProviderService providers) {
		this.providers = providers;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('CASHIER', 'ADMIN')")
	List<WalletProviderOptionResponse> listActive() {
		return providers.listActive().stream().map(WalletProviderOptionResponse::from).toList();
	}

}
