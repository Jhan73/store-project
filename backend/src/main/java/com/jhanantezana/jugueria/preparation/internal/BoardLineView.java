package com.jhanantezana.jugueria.preparation.internal;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.preparation.BoardLineState;

public record BoardLineView(UUID id, UUID sourceLineId, UUID productId, String displayName, int quantity, @Nullable String modifiersText,
		@Nullable String note, boolean allergy, @Nullable UUID stationId, BoardLineState state, boolean waste,
		boolean flaggedUnavailable, @Nullable Instant readyAt) {
}
