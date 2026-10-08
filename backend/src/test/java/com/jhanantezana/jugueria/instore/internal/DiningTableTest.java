package com.jhanantezana.jugueria.instore.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class DiningTableTest {

	static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

	@Test
	void createsAnActiveTableWithTrimmedNameAndArea() {
		var table = new DiningTable("  Mesa 1 ", " Terrace ", 3, NOW);

		assertThat(table.getName()).isEqualTo("Mesa 1");
		assertThat(table.getArea()).isEqualTo("Terrace");
		assertThat(table.getDisplayOrder()).isEqualTo(3);
		assertThat(table.isActive()).isTrue();
		assertThat(table.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void aBlankAreaMeansNoArea() {
		assertThat(new DiningTable("Mesa 1", "   ", 0, NOW).getArea()).isNull();
		assertThat(new DiningTable("Mesa 1", null, 0, NOW).getArea()).isNull();
	}

	@Test
	void changeReplacesTheDefinition() {
		var table = new DiningTable("Mesa 1", "Terrace", 0, NOW);

		table.change(" Barra ", "", 7, NOW.plusSeconds(60));

		assertThat(table.getName()).isEqualTo("Barra");
		assertThat(table.getArea()).isNull();
		assertThat(table.getDisplayOrder()).isEqualTo(7);
		assertThat(table.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
	}

	@Test
	void deactivateAndReactivateToggleActive() {
		var table = new DiningTable("Mesa 1", null, 0, NOW);

		table.deactivate(NOW.plusSeconds(60));
		assertThat(table.isActive()).isFalse();

		table.reactivate(NOW.plusSeconds(120));
		assertThat(table.isActive()).isTrue();
	}

}
