package com.jhanantezana.jugueria.instore.internal;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record SaleVoidView(String reasonCode, @Nullable String note, UUID voidedBy, Instant voidedAt) {
}
