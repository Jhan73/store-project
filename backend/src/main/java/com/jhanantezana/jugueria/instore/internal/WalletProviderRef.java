package com.jhanantezana.jugueria.instore.internal;

import java.util.UUID;

/** The provider a wallet payment was taken with: its id and its name at payment time. */
public record WalletProviderRef(UUID id, String name) {
}
