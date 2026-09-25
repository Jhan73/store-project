package com.jhanantezana.jugueria;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

class ArchitectureTest {

	static final JavaClasses APPLICATION = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackagesOf(BackendApplication.class);

	@Test
	void everyControllerMethodDeclaresWhoMayCallIt() {
		ArchitectureRules.CONTROLLER_METHODS_DECLARE_WHO_MAY_CALL_THEM.allowEmptyShould(true).check(APPLICATION);
	}

	@Test
	void timeIsReadThroughTheClock() {
		ArchitectureRules.TIME_IS_READ_THROUGH_THE_CLOCK.check(APPLICATION);
	}

}
