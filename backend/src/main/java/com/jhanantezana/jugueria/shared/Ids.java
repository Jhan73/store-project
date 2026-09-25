package com.jhanantezana.jugueria.shared;

import java.util.UUID;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

public final class Ids {

	private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

	private Ids() {
	}

	public static UUID newId() {
		return GENERATOR.generate();
	}

}
