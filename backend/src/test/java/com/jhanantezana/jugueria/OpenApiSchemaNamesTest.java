package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import io.swagger.v3.oas.annotations.media.Schema;

// OpenAPI keys schemas by simple name, so two web types sharing one would silently overwrite each other.
class OpenApiSchemaNamesTest {

	@Test
	void noTwoWebTypesShareASchemaName() throws ClassNotFoundException {
		var scanner = new ClassPathScanningCandidateComponentProvider(false) {
			@Override
			protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
				return true;
			}
		};
		scanner.addIncludeFilter(new RegexPatternTypeFilter(java.util.regex.Pattern.compile(".*[.]web[.].*")));
		var byName = new HashMap<String, List<String>>();

		for (var candidate : scanner.findCandidateComponents("com.jhanantezana.jugueria")) {
			var type = Class.forName(candidate.getBeanClassName());
			if (!type.isRecord() && !type.isEnum() || type.getName().matches(".*(IT|Test)([$].*)?")) {
				continue;
			}
			var schema = type.getAnnotation(Schema.class);
			var name = schema != null && !schema.name().isBlank() ? schema.name() : type.getSimpleName();
			byName.computeIfAbsent(name, key -> new ArrayList<>()).add(type.getName());
		}

		Map<String, List<String>> duplicates = new HashMap<>(byName);
		duplicates.values().removeIf(types -> types.size() < 2);
		assertThat(duplicates).isEmpty();
	}

}
