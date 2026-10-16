package com.jhanantezana.jugueria.preparation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ModifierSignatureTest {

	private static final UUID HONEY = UUID.fromString("00000000-0000-7000-8000-000000000001");

	private static final UUID GINGER = UUID.fromString("00000000-0000-7000-8000-000000000002");

	@Test
	void ignoresTheOrderOfTheOptions() {
		assertThat(ModifierSignature.of(List.of(HONEY, GINGER))).isEqualTo(ModifierSignature.of(List.of(GINGER, HONEY)));
	}

	@Test
	void differsWhenAnOptionDiffers() {
		assertThat(ModifierSignature.of(List.of(HONEY))).isNotEqualTo(ModifierSignature.of(List.of(GINGER)));
		assertThat(ModifierSignature.of(List.of(HONEY))).isNotEqualTo(ModifierSignature.of(List.of(HONEY, GINGER)));
	}

	@Test
	void givesNoModifiersTheirOwnStableSignature() {
		assertThat(ModifierSignature.of(List.of())).isEqualTo(ModifierSignature.of(List.of()));
		assertThat(ModifierSignature.of(List.of())).isNotEqualTo(ModifierSignature.of(List.of(HONEY)));
	}

	@Test
	void countsARepeatedOptionTwice() {
		assertThat(ModifierSignature.of(List.of(HONEY, HONEY))).isNotEqualTo(ModifierSignature.of(List.of(HONEY)));
	}

	@Test
	void fitsTheColumn() {
		assertThat(ModifierSignature.of(List.of(HONEY, GINGER))).hasSize(64).matches("[0-9a-f]{64}");
	}

}
