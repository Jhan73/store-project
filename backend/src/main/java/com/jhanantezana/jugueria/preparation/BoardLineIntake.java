package com.jhanantezana.jugueria.preparation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * One line to prepare, as the sending module knew it when it sent it: the name and modifiers are snapshots, so the
 * board never depends on the current catalog. {@code modifierOptionIds} decides which lines count as identical in
 * the all-day view.
 */
public record BoardLineIntake(UUID sourceLineId, UUID productId, String displayName, int quantity,
		@Nullable String modifiersText, List<UUID> modifierOptionIds, @Nullable String note, boolean allergy,
		@Nullable UUID stationId) {

	public static final int MAX_NAME = 200;

	public static final int MAX_MODIFIERS_TEXT = 500;

	public static final int MAX_NOTE = 140;

	public BoardLineIntake {
		Objects.requireNonNull(sourceLineId, "sourceLineId");
		Objects.requireNonNull(productId, "productId");
		if (displayName == null || displayName.isBlank() || displayName.length() > MAX_NAME) {
			throw new IllegalArgumentException("A board line needs a display name of at most " + MAX_NAME + " characters");
		}
		if (quantity <= 0) {
			throw new IllegalArgumentException("A board line needs a positive quantity");
		}
		modifiersText = blankToNull(modifiersText);
		if (modifiersText != null && modifiersText.length() > MAX_MODIFIERS_TEXT) {
			throw new IllegalArgumentException("The modifiers text is longer than " + MAX_MODIFIERS_TEXT + " characters");
		}
		note = blankToNull(note);
		if (note != null && note.length() > MAX_NOTE) {
			throw new IllegalArgumentException("The note is longer than " + MAX_NOTE + " characters");
		}
		modifierOptionIds = List.copyOf(modifierOptionIds);
	}

	private static @Nullable String blankToNull(@Nullable String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}

}
