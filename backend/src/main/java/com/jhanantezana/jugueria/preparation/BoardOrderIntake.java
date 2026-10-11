package com.jhanantezana.jugueria.preparation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * What a sales module hands to the board when an order is paid or a ticket is sent. {@code fireAt} is when
 * preparation should start (scheduled online orders); the card is hidden until then. {@code label} is the table or
 * the customer's first name.
 */
public record BoardOrderIntake(BoardSource source, UUID sourceId, String shortNumber, BoardChannel channel,
		@Nullable String label, Instant sentAt, @Nullable Instant fireAt, List<BoardLineIntake> lines) {

	public static final int MAX_SHORT_NUMBER = 20;

	public static final int MAX_LABEL = 100;

	public BoardOrderIntake {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(sourceId, "sourceId");
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(sentAt, "sentAt");
		if (shortNumber == null || shortNumber.isBlank() || shortNumber.length() > MAX_SHORT_NUMBER) {
			throw new IllegalArgumentException("A board order needs a short number of at most " + MAX_SHORT_NUMBER
					+ " characters");
		}
		label = label == null || label.isBlank() ? null : label.trim();
		if (label != null && label.length() > MAX_LABEL) {
			throw new IllegalArgumentException("The label is longer than " + MAX_LABEL + " characters");
		}
		if (lines.isEmpty()) {
			throw new IllegalArgumentException("A board order needs at least one line");
		}
		lines = List.copyOf(lines);
		if (lines.stream().map(BoardLineIntake::sourceLineId).distinct().count() != lines.size()) {
			throw new IllegalArgumentException("A board order cannot repeat a source line");
		}
	}

}
