package com.jhanantezana.jugueria.instore.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class WalletProviderTest {

	private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

	@Test
	void startsActiveWithATrimmedName() {
		var provider = new WalletProvider("  Yape ", NOW);

		assertThat(provider.getName()).isEqualTo("Yape");
		assertThat(provider.isActive()).isTrue();
		assertThat(provider.snapshot().name()).isEqualTo("Yape");
	}

	@Test
	void renamingKeepsItActiveAndTrimsTheName() {
		var provider = new WalletProvider("Yape", NOW);

		provider.rename(" Plin ", NOW.plusSeconds(1));

		assertThat(provider.getName()).isEqualTo("Plin");
		assertThat(provider.isActive()).isTrue();
		assertThat(provider.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
	}

	@Test
	void deactivatingAndReactivatingFlipsOnlyTheFlag() {
		var provider = new WalletProvider("Yape", NOW);

		provider.deactivate(NOW.plusSeconds(1));
		assertThat(provider.snapshot().active()).isFalse();
		provider.reactivate(NOW.plusSeconds(2));

		assertThat(provider.snapshot().active()).isTrue();
		assertThat(provider.getName()).isEqualTo("Yape");
	}

}
