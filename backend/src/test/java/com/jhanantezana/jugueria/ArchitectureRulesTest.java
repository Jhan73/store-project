package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.jhanantezana.archfixtures.ClockBypass;
import com.jhanantezana.archfixtures.ClockUser;
import com.jhanantezana.archfixtures.GuardedController;
import com.jhanantezana.archfixtures.UnguardedController;
import com.tngtech.archunit.core.importer.ClassFileImporter;

class ArchitectureRulesTest {

	final ClassFileImporter importer = new ClassFileImporter();

	@Test
	void flagsAControllerMethodWithoutAnAccessDecision() {
		var result = ArchitectureRules.CONTROLLER_METHODS_DECLARE_WHO_MAY_CALL_THEM
			.evaluate(importer.importClasses(UnguardedController.class));

		assertThat(result.hasViolation()).isTrue();
		assertThat(result.getFailureReport().getDetails()).singleElement().asString().contains("list()");
	}

	@Test
	void acceptsGuardedAndDeliberatelyPublicMethods() {
		var result = ArchitectureRules.CONTROLLER_METHODS_DECLARE_WHO_MAY_CALL_THEM
			.evaluate(importer.importClasses(GuardedController.class));

		assertThat(result.hasViolation()).isFalse();
	}

	@Test
	void flagsEveryWayOfReadingTheSystemClock() {
		var result = ArchitectureRules.TIME_IS_READ_THROUGH_THE_CLOCK
			.evaluate(importer.importClasses(ClockBypass.class));

		assertThat(result.getFailureReport().getDetails()).hasSize(7);
	}

	@Test
	void acceptsTimeReadThroughAClock() {
		var result = ArchitectureRules.TIME_IS_READ_THROUGH_THE_CLOCK
			.evaluate(importer.importClasses(ClockUser.class));

		assertThat(result.hasViolation()).isFalse();
	}

}
