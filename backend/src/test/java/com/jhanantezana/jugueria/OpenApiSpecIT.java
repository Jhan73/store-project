package com.jhanantezana.jugueria;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
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
	JsonMapper jsonMapper;

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
	void tableRequestsMarkOnlyWhatTheyRequire() throws IOException {
		var schemas = new JsonMapper().readTree(generate()).path("components").path("schemas");

		var create = new ArrayList<String>();
		schemas.path("CreateTableRequest").path("required").forEach(name -> create.add(name.asString()));
		var update = new ArrayList<String>();
		schemas.path("UpdateTableRequest").path("required").forEach(name -> update.add(name.asString()));

		assertThat(create).containsExactly("name");
		assertThat(update).containsExactlyInAnyOrder("name", "displayOrder");
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

	@Test
	void requiresEveryRequestComponentThatIsNotNullable() throws IOException {
		var schemas = schemas();

		var category = schemas.path("CreateCategoryRequest");
		assertThat(names(category.path("required"))).containsExactlyInAnyOrder("name", "displayOrder");
		assertThat(types(category.path("properties").path("stationId"))).containsExactlyInAnyOrder("string", "null");
		assertThat(types(category.path("properties").path("name"))).containsExactly("string");

		var product = schemas.path("SaveProductRequest");
		assertThat(names(product.path("required"))).contains("categoryId", "name", "price", "displayOrder",
				"quickSalePinned");
		assertThat(names(product.path("required"))).doesNotContain("description", "allergens", "modifierGroupIds");
	}

	@Test
	void requiresEveryResponseComponentAndMarksTheNullableOnesAsSuch() throws IOException {
		var product = schemas().path("ProductResponse");

		assertThat(names(product.path("required"))).containsExactlyInAnyOrder("id", "name", "categoryId", "price",
				"displayOrder", "quickSalePinned", "active", "available", "allergens", "modifierGroupIds", "etag");
		assertThat(types(product.path("properties").path("description"))).containsExactlyInAnyOrder("string", "null");
		assertThat(types(product.path("properties").path("imageUrl"))).containsExactlyInAnyOrder("string", "null");
		assertThat(types(product.path("properties").path("etag"))).containsExactly("string");
	}

	@Test
	void marksANullableReferenceAsOneOfTheTypeAndNull() throws IOException {
		var minimumOrder = schemas().path("DeliveryZoneResponse").path("properties").path("minimumOrder");

		assertThat(minimumOrder.path("oneOf").get(0).path("$ref").asString()).isEqualTo("#/components/schemas/Money");
		assertThat(minimumOrder.path("oneOf").get(1).path("type").asString()).isEqualTo("null");
		assertThat(schemas().path("DeliveryZoneResponse").path("properties").path("fee").has("$ref")).isTrue();
	}

	@Test
	void everySchemaPropertyIsRequiredOrNullableOrAnOptionalRequestInput() throws IOException {
		var schemas = schemas();

		schemas.properties().forEach(schema -> {
			var name = schema.getKey();
			if (!name.endsWith("Response")) {
				return;
			}
			var required = names(schema.getValue().path("required"));
			schema.getValue().path("properties").properties().forEach(property -> {
				var nullable = types(property.getValue()).contains("null")
						|| property.getValue().path("oneOf").toString().contains("\"null\"");
				assertThat(nullable || required.contains(property.getKey()))
					.as("%s.%s must be required or nullable", name, property.getKey())
					.isTrue();
			});
		});
	}

	// Nulls are written, not omitted, so a response schema must list every component, nullable or not.
	@Test
	void serializedResponsesCarryExactlyThePropertiesTheSpecDescribes() throws Exception {
		var schemas = schemas();
		var scanner = new ClassPathScanningCandidateComponentProvider(false) {
			@Override
			protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
				return true;
			}
		};
		scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*[.]web[.][A-Za-z]*Response")));
		var checked = new ArrayList<String>();

		for (var candidate : scanner.findCandidateComponents("com.jhanantezana.jugueria")) {
			var type = Class.forName(candidate.getBeanClassName());
			var schema = schemas.path(type.getSimpleName());
			if (!type.isRecord() || schema.isMissingNode()) {
				continue;
			}
			var constructor = type.getDeclaredConstructors()[0];
			constructor.setAccessible(true);
			var arguments = Arrays.stream(constructor.getParameterTypes()).map(OpenApiSpecIT::blank).toArray();
			var json = jsonMapper.readTree(jsonMapper.writeValueAsString(constructor.newInstance(arguments)));

			assertThat(names(schema.path("required"))).as(type.getSimpleName()).isSubsetOf(propertyNames(json));
			assertThat(propertyNames(json)).as(type.getSimpleName())
				.containsExactlyInAnyOrderElementsOf(propertyNames(schema.path("properties")));
			checked.add(type.getSimpleName());
		}

		assertThat(checked).contains("ProductResponse", "TableGridResponse", "StoreSettingsResponse", "StaffResponse");
	}

	private static Object blank(Class<?> type) {
		if (type.isPrimitive()) {
			return java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(type, 1), 0);
		}
		return List.class.isAssignableFrom(type) ? List.of() : java.util.Set.class.isAssignableFrom(type)
				? java.util.Set.of() : null;
	}

	private static List<String> propertyNames(JsonNode object) {
		var names = new ArrayList<String>();
		object.propertyNames().forEach(names::add);
		return names;
	}

	@Test
	void describesTimesOfDayAsTheStringsJacksonWrites() throws IOException {
		var opensAt = schemas().path("OpeningHourResponse").path("properties").path("opensAt");

		assertThat(types(opensAt)).containsExactlyInAnyOrder("string", "null");
		assertThat(opensAt.has("format")).isFalse();
		assertThat("08:00:00").matches(opensAt.path("pattern").asString());
		assertThat("24:00:00").doesNotMatch(opensAt.path("pattern").asString());
	}

	@Test
	void listsValidationFailedOnlyWhereSomethingIsValidated() throws IOException {
		var paths = new JsonMapper().readTree(generate()).path("paths");

		var byId = paths.path("/api/v1/staff/{id}").path("get").path("responses");
		assertThat(codes(byId.path("400"))).containsExactly("common.malformed-request");
		var withBody = paths.path("/api/v1/admin/categories").path("post").path("responses");
		assertThat(codes(withBody.path("400"))).containsExactly("common.malformed-request", "common.validation-failed");
	}

	private JsonNode schemas() throws IOException {
		return new JsonMapper().readTree(generate()).path("components").path("schemas");
	}

	private static List<String> names(JsonNode array) {
		var names = new ArrayList<String>();
		array.forEach(name -> names.add(name.asString()));
		return names;
	}

	// "type" is a string for a plain schema and an array once null is allowed.
	private static List<String> types(JsonNode schema) {
		var type = schema.path("type");
		return type.isArray() ? names(type) : type.isMissingNode() ? List.of() : List.of(type.asString());
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
