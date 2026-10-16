package com.jhanantezana.jugueria.preparation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.jhanantezana.jugueria.preparation.BoardChannel;

class FirstNameTest {

	@ParameterizedTest
	@EnumSource(value = BoardChannel.class, names = { "COUNTER", "PICKUP", "DELIVERY" })
	void takesTheFirstWordOfTheLabelWhenTheCardIsForAPerson(BoardChannel channel) {
		assertThat(FirstName.of(channel, "Ana Perez Soto")).isEqualTo("Ana");
	}

	@Test
	void keepsASingleWordAsItIs() {
		assertThat(FirstName.of(BoardChannel.COUNTER, "Ana")).isEqualTo("Ana");
	}

	@Test
	void ignoresSurroundingAndRepeatedWhitespaceIncludingNonBreakingSpaces() {
		assertThat(FirstName.of(BoardChannel.COUNTER, "  \t Ana  Perez ")).isEqualTo("Ana");
	}

	@Test
	void neverShowsATableLabelBecauseItIsNotAName() {
		assertThat(FirstName.of(BoardChannel.TABLE, "Table 4")).isNull();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "\t\n", " " })
	void hasNoNameWhenTheLabelIsMissingOrBlank(String label) {
		assertThat(FirstName.of(BoardChannel.COUNTER, label)).isNull();
	}

	@Test
	void cutsAVeryLongWordSoItCannotTakeOverTheScreen() {
		var name = FirstName.of(BoardChannel.COUNTER, "A".repeat(80));

		assertThat(name).hasSize(FirstName.MAX_LENGTH);
	}

	@Test
	void doesNotCutInTheMiddleOfAnEmoji() {
		var name = FirstName.of(BoardChannel.COUNTER, "😀".repeat(40));

		assertThat(name.codePointCount(0, name.length())).isEqualTo(FirstName.MAX_LENGTH);
		assertThat(Character.isLowSurrogate(name.charAt(0))).isFalse();
	}

}
