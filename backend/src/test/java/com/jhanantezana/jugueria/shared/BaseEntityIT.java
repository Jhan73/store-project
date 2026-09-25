package com.jhanantezana.jugueria.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.jhanantezana.jugueria.TestcontainersConfiguration;

@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator" })
@Import(TestcontainersConfiguration.class)
class BaseEntityIT {

	@Autowired
	ProbeRepository probes;

	@AfterEach
	void cleanUp() {
		probes.deleteAll();
	}

	@Test
	void insertsANewEntityWithoutMergingIt() {
		var entity = new ProbeEntity(Money.of("12.50", MoneyTest.PEN));

		// merge() would return a managed copy; persist() keeps the same instance.
		assertThat(probes.save(entity)).isSameAs(entity);
	}

	@Test
	void roundTripsMoneyAndIsNotNewOnceLoaded() {
		var entity = probes.save(new ProbeEntity(Money.of("12.50", MoneyTest.PEN)));

		var loaded = probes.findById(entity.getId()).orElseThrow();

		assertThat(loaded.getPrice()).isEqualTo(Money.of("12.50", MoneyTest.PEN));
		assertThat(loaded.isNew()).isFalse();
		assertThat(loaded).isEqualTo(entity);
	}

}
