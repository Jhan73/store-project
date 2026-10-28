package com.jhanantezana.jugueria.instore;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.shared.Role;

/** {@code before} is null when the provider was created. */
public record WalletProviderChanged(UUID providerId, WalletProviderChangeKind kind,
		@Nullable WalletProviderSnapshot before, WalletProviderSnapshot after, @Nullable UUID actorId,
		@Nullable Role actorRole, Instant occurredAt) {
}
