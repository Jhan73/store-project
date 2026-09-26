package com.jhanantezana.jugueria;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.annotation.security.PermitAll;

final class ArchitectureRules {

	static final ArchRule CONTROLLER_METHODS_DECLARE_WHO_MAY_CALL_THEM = methods().that()
		.areDeclaredInClassesThat()
		.areAnnotatedWith(RestController.class)
		.and()
		.areMetaAnnotatedWith(RequestMapping.class)
		.should()
		.beAnnotatedWith(PreAuthorize.class)
		.orShould()
		.beAnnotatedWith(PermitAll.class)
		.because("a forgotten role check must fail the build instead of opening an endpoint");

	// Matches calls and method references (Instant::now) alike.
	static final ArchRule TIME_IS_READ_THROUGH_THE_CLOCK = noClasses().should()
		.accessTargetWhere(readsTheSystemClock())
		.because("time must come from the injected Clock so tests can control it");

	// Hibernate's timestamp generators read their own clock, bypassing the injected one.
	static final ArchRule NO_HIBERNATE_GENERATED_TIMESTAMPS = noFields().should()
		.beAnnotatedWith(CreationTimestamp.class)
		.orShould()
		.beAnnotatedWith(UpdateTimestamp.class)
		.because("timestamps must come from the injected Clock, like every other persisted moment");

	private record Signature(Class<?> owner, String name, List<Class<?>> parameters) {

		boolean matches(CodeUnitAccessTarget target) {
			return target.getOwner().isEquivalentTo(owner) && target.getName().equals(name)
					&& target.getRawParameterTypes().stream().map(JavaClass::getName).toList()
						.equals(parameters.stream().map(Class::getName).toList());
		}

	}

	private static final List<Signature> SYSTEM_CLOCK_READS = List.of(
			new Signature(Instant.class, "now", List.of()),
			new Signature(LocalDate.class, "now", List.of()),
			new Signature(LocalDate.class, "now", List.of(ZoneId.class)),
			new Signature(LocalDateTime.class, "now", List.of()),
			new Signature(LocalDateTime.class, "now", List.of(ZoneId.class)),
			new Signature(LocalTime.class, "now", List.of()),
			new Signature(LocalTime.class, "now", List.of(ZoneId.class)),
			new Signature(ZonedDateTime.class, "now", List.of()),
			new Signature(ZonedDateTime.class, "now", List.of(ZoneId.class)),
			new Signature(OffsetDateTime.class, "now", List.of()),
			new Signature(OffsetDateTime.class, "now", List.of(ZoneId.class)),
			new Signature(Date.class, "<init>", List.of()),
			new Signature(System.class, "currentTimeMillis", List.of()));

	private static DescribedPredicate<JavaAccess<?>> readsTheSystemClock() {
		return DescribedPredicate.describe("reads the system clock",
				access -> access.getTarget() instanceof CodeUnitAccessTarget target
						&& SYSTEM_CLOCK_READS.stream().anyMatch(read -> read.matches(target)));
	}

	private ArchitectureRules() {
	}

}
