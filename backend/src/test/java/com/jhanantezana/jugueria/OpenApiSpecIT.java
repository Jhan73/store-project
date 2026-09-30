package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.jhanantezana.jugueria.shared.Role;
import com.jhanantezana.testsupport.AuthenticatedAs;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// The app never serves the spec (springdoc is off outside this test), so the file is generated here.
// Regenerate: ./mvnw verify -Dit.test=OpenApiSpecIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dopenapi.write=true
@SpringBootTest(properties = { "spring.flyway.user=migrator", "spring.flyway.password=migrator",
		"springdoc.api-docs.enabled=true" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiSpecIT {

	static final Path COMMITTED = Path.of("api", "openapi.json");

	@Autowired
	MockMvcTester mvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@Test
	void committedSpecMatchesTheControllers() throws IOException {
		var generated = generate();

		if (Boolean.getBoolean("openapi.write")) {
			Files.createDirectories(COMMITTED.getParent());
			Files.writeString(COMMITTED, generated, StandardCharsets.UTF_8);
			return;
		}

		assertThat(COMMITTED).as("backend/api/openapi.json is missing; regenerate it with -Dopenapi.write=true")
			.exists();
		var committed = Files.readString(COMMITTED, StandardCharsets.UTF_8).replace("\r\n", "\n");
		assertThat(generated).as("""
				backend/api/openapi.json is out of date. From backend/, run:
				./mvnw verify -Dit.test=OpenApiSpecIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dopenapi.write=true
				and commit the result.""").isEqualTo(committed);
	}

	@Test
	void documentsEveryApiEndpoint() throws IOException {
		var paths = new JsonMapper().readTree(generate()).path("paths");
		var missing = new ArrayList<String>();

		handlerMapping.getHandlerMethods().forEach((info, handler) -> {
			var patterns = info.getPathPatternsCondition();
			if (patterns == null) {
				return;
			}
			for (var pattern : patterns.getPatternValues()) {
				if (!pattern.startsWith("/api/v1/")) {
					continue;
				}
				for (var method : info.getMethodsCondition().getMethods()) {
					if (!documented(paths, pattern, HttpMethod.valueOf(method.name()))) {
						missing.add(method + " " + pattern);
					}
				}
			}
		});

		assertThat(missing).isEmpty();
	}

	@Test
	void everyOperationHasASuccessResponseAndAUniqueId() throws IOException {
		var operations = new ArrayList<JsonNode>();
		new JsonMapper().readTree(generate()).path("paths").forEach(item -> item.forEach(operations::add));

		assertThat(operations).isNotEmpty().allSatisfy(operation -> {
			var statuses = new ArrayList<String>();
			operation.path("responses").propertyNames().forEach(statuses::add);
			assertThat(statuses).anyMatch(status -> status.startsWith("2"));
		});
		assertThat(operations.stream().map(operation -> operation.path("operationId").asString()))
			.doesNotHaveDuplicates();
	}

	@Test
	void documentsSuccessStatusAndErrorCodesPerEndpoint() throws IOException {
		var paths = new JsonMapper().readTree(generate()).path("paths");

		var create = paths.path("/api/v1/admin/categories").path("post").path("responses");
		assertThat(create.has("201")).isTrue();
		assertThat(create.has("200")).isFalse();
		assertThat(codes(create.path("409"))).containsExactly("catalog.category-name-already-used");
		assertThat(codes(create.path("422"))).containsExactly("catalog.unknown-station");
		assertThat(codes(create.path("401"))).containsExactly("auth.unauthenticated");

		var delete = paths.path("/api/v1/admin/modifier-groups/{id}").path("delete").path("responses");
		assertThat(delete.has("204")).isTrue();
		assertThat(codes(delete.path("412"))).containsExactly("common.precondition-failed");

		var login = paths.path("/api/v1/auth/login").path("post").path("responses");
		assertThat(codes(login.path("401"))).containsExactly("auth.invalid-credentials");
		assertThat(login.has("403")).isFalse();
	}

	@Test
	void describesMoneyAndCurrenciesAsTheyAreSerialized() throws IOException {
		var schemas = new JsonMapper().readTree(generate()).path("components").path("schemas");

		assertThat(schemas.path("Money").path("properties").path("amount").path("type").asString()).isEqualTo("string");
		assertThat(schemas.has("Currency")).isFalse();
		var currencies = new ArrayList<String>();
		schemas.forEach(schema -> schema.path("properties").properties().forEach(property -> {
			if (property.getKey().equals("currency")) {
				currencies.add(property.getValue().path("type").asString());
			}
		}));
		assertThat(currencies).isNotEmpty().containsOnly("string");
	}

	@Test
	void marksIfMatchAsRequiredAndTheImageUploadBodyAsRequired() throws IOException {
		var paths = new JsonMapper().readTree(generate()).path("paths");
		var ifMatch = new ArrayList<JsonNode>();
		paths.forEach(item -> item.forEach(operation -> operation.path("parameters").forEach(parameter -> {
			if (parameter.path("name").asString().equals("If-Match")) {
				ifMatch.add(parameter);
			}
		})));

		assertThat(ifMatch).isNotEmpty().allSatisfy(parameter -> assertThat(parameter.path("required").asBoolean()).isTrue());
		assertThat(paths.path("/api/v1/admin/products/{id}/image").path("put").path("requestBody").path("required")
			.asBoolean()).isTrue();
	}

	@Test
	void describesProblemInstanceAsAUriReference() throws IOException {
		var problem = new JsonMapper().readTree(generate()).path("components").path("schemas").path("Problem");

		assertThat(problem.path("properties").path("instance").path("format").asString()).isEqualTo("uri-reference");
	}

	private static List<String> codes(JsonNode response) {
		var codes = new ArrayList<String>();
		response.path("x-error-codes").forEach(code -> codes.add(code.asString()));
		return codes;
	}

	private static boolean documented(JsonNode paths, String pattern, HttpMethod method) {
		return paths.path(pattern).has(method.name().toLowerCase());
	}

	private String generate() throws IOException {
		var result = mvc.get()
			.uri("/v3/api-docs")
			.with(AuthenticatedAs.user(UUID.randomUUID(), Role.ADMIN))
			.exchange();
		assertThat(result).hasStatusOk();
		var body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		return body.replace("\r\n", "\n") + "\n";
	}

}
