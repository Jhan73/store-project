package com.jhanantezana.jugueria.preparation.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.preparation.BoardChannel;
import com.jhanantezana.jugueria.preparation.BoardOrderStatus;
import com.jhanantezana.jugueria.preparation.BoardSource;
import com.jhanantezana.jugueria.preparation.internal.BoardOrderView;

record BoardOrderResponse(UUID id, BoardSource source, UUID sourceId, String shortNumber, BoardChannel channel,
		@Nullable String label, BoardOrderStatus status, Instant sentAt, @Nullable Instant fireAt,
		@Nullable Instant startedAt, @Nullable Instant readyAt, @Nullable Instant handedOverAt,
		List<BoardLineResponse> lines, List<MarkerResponse> markers) {

	static BoardOrderResponse from(BoardOrderView order) {
		return new BoardOrderResponse(order.id(), order.source(), order.sourceId(), order.shortNumber(),
				order.channel(), order.label(), order.status(), order.sentAt(), order.fireAt(), order.startedAt(),
				order.readyAt(), order.handedOverAt(), order.lines().stream().map(BoardLineResponse::from).toList(),
				order.markers().stream().map(MarkerResponse::from).toList());
	}

}
