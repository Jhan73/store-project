package com.jhanantezana.jugueria;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

	@Test
	void modulesRespectTheirBoundaries() {
		ApplicationModules.of(BackendApplication.class).verify();
	}

}
