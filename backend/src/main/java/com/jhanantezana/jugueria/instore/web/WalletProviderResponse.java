package com.jhanantezana.jugueria.instore.web;

import java.util.UUID;

import com.jhanantezana.jugueria.instore.internal.WalletProvider;
import com.jhanantezana.jugueria.shared.ETags;

record WalletProviderResponse(UUID id, String name, boolean active, String etag) {

	static WalletProviderResponse from(WalletProvider provider) {
		return new WalletProviderResponse(provider.getId(), provider.getName(), provider.isActive(),
				ETags.format(provider.getVersion()));
	}

}
