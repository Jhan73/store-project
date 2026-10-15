package com.jhanantezana.jugueria.preparation.internal;

import org.jspecify.annotations.Nullable;

import com.jhanantezana.jugueria.preparation.BoardChannel;

/** What a card's label may show on the customer screen: a person's first word, never a table. */
final class FirstName {

	static final int MAX_LENGTH = 20;

	private FirstName() {
	}

	static @Nullable String of(BoardChannel channel, @Nullable String label) {
		if (channel == BoardChannel.TABLE || label == null) {
			return null;
		}
		var start = 0;
		while (start < label.length() && isSpace(label.codePointAt(start))) {
			start += Character.charCount(label.codePointAt(start));
		}
		var end = start;
		while (end < label.length() && !isSpace(label.codePointAt(end))) {
			end += Character.charCount(label.codePointAt(end));
		}
		if (start == end) {
			return null;
		}
		var word = label.substring(start, end);
		return word.codePointCount(0, word.length()) > MAX_LENGTH ? word.substring(0, word.offsetByCodePoints(0, MAX_LENGTH))
				: word;
	}

	private static boolean isSpace(int codePoint) {
		return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
	}

}
