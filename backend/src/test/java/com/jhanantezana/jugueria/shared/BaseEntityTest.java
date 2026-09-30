package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BaseEntityTest {

	@Test
	void assignsAVersion7IdAtConstruction() {
		assertThat(new ProbeEntity(Money.zero(MoneyTest.PEN)).getId().version()).isEqualTo(7);
	}

	@Test
	void isNewUntilPersistedOrLoaded() {
		var entity = new ProbeEntity(Money.zero(MoneyTest.PEN));
		assertThat(entity.isNew()).isTrue();

		entity.markNotNew();

		assertThat(entity.isNew()).isFalse();
	}

	@Test
	void comparesByIdOnly() {
		var entity = new ProbeEntity(Money.zero(MoneyTest.PEN));
		var other = new ProbeEntity(Money.zero(MoneyTest.PEN));

		assertThat(entity).isEqualTo(entity).isNotEqualTo(other).hasSameHashCodeAs(entity);
	}

}
