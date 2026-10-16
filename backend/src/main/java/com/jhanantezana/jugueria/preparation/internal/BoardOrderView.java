package com.jhanantezana.jugueria.preparation.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.preparation.BoardChannel;
import com.jhanantezana.jugueria.preparation.BoardOrderStatus;
import com.jhanantezana.jugueria.preparation.BoardSource;

/** A card as the board shows it: its lines and the markers nobody has acknowledged yet. */
public record BoardOrderView(UUID id, BoardSource source, UUID sourceId, String shortNumber, BoardChannel channel,
		@Nullable String label, BoardOrderStatus status, Instant sentAt, @Nullable Instant fireAt,
		@Nullable Instant startedAt, @Nullable Instant readyAt, @Nullable Instant handedOverAt,
		List<BoardLineView> lines, List<MarkerView> markers) {
}
