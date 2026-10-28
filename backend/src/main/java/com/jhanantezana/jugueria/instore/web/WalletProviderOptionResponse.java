package com.jhanantezana.jugueria.instore.web;

import java.util.UUID;

import com.jhanantezana.jugueria.instore.internal.WalletProvider;

record WalletProviderOptionResponse(UUID id, String name) {

	static WalletProviderOptionResponse from(WalletProvider provider) {
		return new WalletProviderOptionResponse(provider.getId(), provider.getName());
	}

}
