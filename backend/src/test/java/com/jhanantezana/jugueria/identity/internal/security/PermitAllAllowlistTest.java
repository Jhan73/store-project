package com.jhanantezana.jugueria.identity.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMapping;

import com.jhanantezana.jugueria.BackendApplication;
import com.jhanantezana.jugueria.identity.internal.security.SecurityConfiguration.PublicRoute;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import jakarta.annotation.security.PermitAll;

// Cross-checks SecurityConfiguration.PUBLIC_ROUTES against @PermitAll handler methods in both
// directions, so the filter-chain allowlist and the controllers it lets through cannot drift apart.
class PermitAllAllowlistTest {

	static final JavaClasses APPLICATION = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackagesOf(BackendApplication.class);

	@Test
	void everyPermitAllHandlerIsOnTheAllowlist() {
		assertThat(permitAllRoutes()).allSatisfy(route -> assertThat(SecurityConfiguration.PUBLIC_ROUTES)
			.as("route %s is not in SecurityConfiguration.PUBLIC_ROUTES", route)
			.contains(route));
	}

	@Test
	void everyControllerBackedAllowlistEntryHasAPermitAllHandler() {
		var permitAllRoutes = permitAllRoutes();

		var controllerBackedRoutes = SecurityConfiguration.PUBLIC_ROUTES.stream()
			.filter(PublicRoute::backedByController)
			.collect(Collectors.toSet());

		assertThat(controllerBackedRoutes).allSatisfy(route -> assertThat(permitAllRoutes)
			.as("route %s has no @PermitAll handler method", route)
			.contains(route));
	}

	private static Set<PublicRoute> permitAllRoutes() {
		return APPLICATION.stream()
			.flatMap(javaClass -> javaClass.getMethods().stream())
			.filter(method -> method.isAnnotatedWith(PermitAll.class))
			.map(method -> routeOf(method.reflect()))
			.collect(Collectors.toSet());
	}

	private static PublicRoute routeOf(Method method) {
		var methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
		var typeMapping = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), RequestMapping.class);
		var httpMethod = HttpMethod.valueOf(methodMapping.method()[0].name());
		var typePath = typeMapping == null || typeMapping.path().length == 0 ? "" : typeMapping.path()[0];
		return new PublicRoute(httpMethod, typePath + methodMapping.path()[0], true);
	}

}
