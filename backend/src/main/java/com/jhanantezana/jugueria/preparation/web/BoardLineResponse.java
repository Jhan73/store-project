package com.jhanantezana.jugueria.preparation.web;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.preparation.BoardLineState;
import com.jhanantezana.jugueria.preparation.internal.BoardLineView;

record BoardLineResponse(UUID id, UUID sourceLineId, UUID productId, String displayName, int quantity, @Nullable String modifiersText,
		@Nullable String note, boolean allergy, @Nullable UUID stationId, BoardLineState state, boolean waste,
		boolean comped, boolean flaggedUnavailable, @Nullable Instant readyAt) {

	static BoardLineResponse from(BoardLineView line) {
		return new BoardLineResponse(line.id(), line.sourceLineId(), line.productId(), line.displayName(), line.quantity(),
				line.modifiersText(), line.note(), line.allergy(), line.stationId(), line.state(), line.waste(),
				line.comped(), line.flaggedUnavailable(), line.readyAt());
	}

}
