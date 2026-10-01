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

import com.jhanantezana.jugueria.shared.Money;
import com.jhanantezana.jugueria.shared.PageResponse;
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
				"displayOrder", "quickSalePinned", "active", "available", "allergens", "modifierGroupIds", "etag", "description",
				"imageUrl");
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
	void everyResponsePropertyIsRequiredAndAnOptionalRequestInputIsNullable() throws IOException {
		schemas().properties().forEach(schema -> {
			var name = schema.getKey();
			if (name.equals("Problem") || name.equals("ErrorCode") || name.endsWith("Request")) {
				return;
			}
			assertThat(names(schema.getValue().path("required"))).as(name)
				.containsExactlyInAnyOrderElementsOf(propertyNames(schema.getValue().path("properties")));
		});
		var table = schemas().path("CreateTableRequest");
		assertThat(names(table.path("required"))).containsExactly("name");
		assertThat(table.path("properties").path("displayOrder").path("default").asInt(-1)).isZero();
	}

	// Nulls are written, not omitted, so a response schema lists every component, nullable or not.
	@Test
	void serializedResponsesCarryExactlyThePropertiesTheSpecDescribes() throws Exception {
		var schemas = schemas();
		var checked = new ArrayList<String>();

		for (var type : responseRecords(schemas)) {
			if (type == Money.class) {
				continue; // refuses nulls, so only the sample test covers it
			}
			var name = schemaName(type);
			var empty = jsonMapper.readTree(jsonMapper.writeValueAsString(instantiate(type, OpenApiSpecIT::blank)));
			assertThat(names(schemas.path(name).path("required"))).as(name).isSubsetOf(propertyNames(empty));
			assertThat(propertyNames(empty)).as(name)
				.containsExactlyInAnyOrderElementsOf(propertyNames(schemas.path(name).path("properties")));
			checked.add(name);
		}

		assertThat(checked).contains("ProductResponse", "TableGridResponse", "StoreSettingsResponse", "StaffResponse",
				"MenuCategory", "MenuProduct", "MenuModifierGroup", "MenuModifierOption", "ModifierOptionResponse");
	}

	@Test
	void serializedSampleValuesConformToTheirSchemas() throws Exception {
		var schemas = schemas();

		for (var type : responseRecords(schemas)) {
			var json = jsonMapper.readTree(jsonMapper.writeValueAsString(instantiate(type, OpenApiSpecIT::sample)));
			assertConforms(json, schemas.path(schemaName(type)), schemas, schemaName(type));
		}
		var product = Class.forName("com.jhanantezana.jugueria.catalog.web.ProductResponse");
		var page = new PageResponse<>(List.of(instantiate(product, OpenApiSpecIT::sample)), 0, 20, 1L, 1);
		assertConforms(jsonMapper.readTree(jsonMapper.writeValueAsString(page)),
				schemas.path("PageResponseProductResponse"), schemas, "PageResponseProductResponse");
	}

	private static List<Class<?>> responseRecords(JsonNode schemas) throws ClassNotFoundException {
		var scanner = new ClassPathScanningCandidateComponentProvider(false) {
			@Override
			protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
				return true;
			}
		};
		scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*[.]web[.].*")));
		var types = new ArrayList<Class<?>>(List.of(Money.class));
		for (var candidate : scanner.findCandidateComponents("com.jhanantezana.jugueria")) {
			var type = Class.forName(candidate.getBeanClassName());
			if (type.isRecord() && !type.getName().matches(".*(IT|Test)([$].*)?") && !schemaName(type).endsWith("Request")
					&& !schemas.path(schemaName(type)).isMissingNode()) {
				types.add(type);
			}
		}
		return types;
	}

	private static String schemaName(Class<?> type) {
		var schema = type.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
		return schema != null && !schema.name().isBlank() ? schema.name() : type.getSimpleName();
	}

	private static Object instantiate(Class<?> type, java.util.function.Function<java.lang.reflect.Type, Object> values)
			throws ReflectiveOperationException {
		var constructor = type.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		return constructor.newInstance(Arrays.stream(constructor.getGenericParameterTypes()).map(values).toArray());
	}

	private static Class<?> rawType(java.lang.reflect.Type type) {
		return type instanceof Class<?> c ? c : (Class<?>) ((java.lang.reflect.ParameterizedType) type).getRawType();
	}

	private static Object blank(java.lang.reflect.Type type) {
		var raw = rawType(type);
		if (raw.isPrimitive()) {
			return java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(raw, 1), 0);
		}
		return List.class.isAssignableFrom(raw) ? List.of() : java.util.Set.class.isAssignableFrom(raw)
				? java.util.Set.of() : null;
	}

	// A non-null value for every component, so the type and format claims meet real JSON.
	private static Object sample(java.lang.reflect.Type type) {
		try {
			var raw = rawType(type);
			var arguments = type instanceof java.lang.reflect.ParameterizedType p ? p.getActualTypeArguments()
					: new java.lang.reflect.Type[0];
			if (raw == String.class) {
				return "text";
			}
			if (raw == UUID.class) {
				return UUID.randomUUID();
			}
			if (raw == java.time.Instant.class) {
				return java.time.Instant.parse("2026-01-02T03:04:05Z");
			}
			if (raw == java.time.LocalTime.class) {
				return java.time.LocalTime.of(8, 0);
			}
			if (raw == java.math.BigDecimal.class) {
				return new java.math.BigDecimal("12.50");
			}
			if (raw == java.util.Currency.class) {
				return java.util.Currency.getInstance("PEN");
			}
			if (raw == Money.class) {
				return Money.of("12.50", java.util.Currency.getInstance("PEN"));
			}
			if (raw.isEnum()) {
				return raw.getEnumConstants()[0];
			}
			if (List.class.isAssignableFrom(raw)) {
				return List.of(sample(arguments[0]));
			}
			if (java.util.Set.class.isAssignableFrom(raw)) {
				return java.util.Set.of(sample(arguments[0]));
			}
			if (java.util.Map.class.isAssignableFrom(raw)) {
				return java.util.Map.of("key", "value");
			}
			if (raw.isRecord()) {
				return instantiate(raw, OpenApiSpecIT::sample);
			}
			if (raw == boolean.class) {
				return true;
			}
			return blank(raw) instanceof Number ? (Object) 1 : blank(raw);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void assertConforms(JsonNode value, JsonNode schema, JsonNode schemas, String where) {
		if (schema.has("$ref")) {
			var target = schema.path("$ref").asString();
			assertConforms(value, schemas.path(target.substring(target.lastIndexOf('/') + 1)), schemas, where);
			return;
		}
		if (schema.has("oneOf")) {
			var matches = 0;
			for (var option : schema.path("oneOf")) {
				try {
					assertConforms(value, option, schemas, where);
					matches++;
				}
				catch (AssertionError ignored) {
					// another branch may fit
				}
			}
			assertThat(matches).as(where).isPositive();
			return;
		}
		var declared = types(schema);
		var actual = value.isNull() ? "null" : value.isString() ? "string" : value.isBoolean() ? "boolean"
				: value.isIntegralNumber() ? "integer" : value.isNumber() ? "number" : value.isArray() ? "array" : "object";
		assertThat(declared.contains(actual) || actual.equals("integer") && declared.contains("number"))
			.as("%s is %s but the spec says %s", where, actual, declared)
			.isTrue();
		if (value.isString() && schema.has("pattern")) {
			assertThat(value.asString()).as(where).matches(schema.path("pattern").asString());
		}
		if (value.isString() && schema.has("enum")) {
			assertThat(names(schema.path("enum"))).as(where).contains(value.asString());
		}
		if (value.isArray() && schema.has("items")) {
			value.forEach(item -> assertConforms(item, schema.path("items"), schemas, where + "[]"));
		}
		if (value.isObject() && schema.has("properties")) {
			assertThat(propertyNames(value)).as(where)
				.containsExactlyInAnyOrderElementsOf(propertyNames(schema.path("properties")));
			value.properties().forEach(property -> assertConforms(property.getValue(),
					schema.path("properties").path(property.getKey()), schemas, where + "." + property.getKey()));
		}
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

	@Test
	void documentsNotAcceptableWhereABodyIsReturnedAndUnsupportedMediaTypeWhereOneIsRead() throws IOException {
		var paths = new JsonMapper().readTree(generate()).path("paths");

		var json = paths.path("/api/v1/admin/categories").path("post").path("responses");
		assertThat(codes(json.path("415"))).containsExactly("common.unsupported-media-type");
		assertThat(codes(json.path("406"))).containsExactly("common.not-acceptable");
		var upload = paths.path("/api/v1/admin/products/{id}/image").path("put").path("responses");
		assertThat(codes(upload.path("415"))).containsExactly("common.unsupported-media-type");
		var read = paths.path("/api/v1/staff/{id}").path("get").path("responses");
		assertThat(read.has("415")).isFalse();
		assertThat(codes(read.path("406"))).containsExactly("common.not-acceptable");
		var bodyless = paths.path("/api/v1/admin/categories/{id}/deactivate").path("post").path("responses");
		assertThat(bodyless.has("415")).isFalse();
		assertThat(bodyless.has("406")).isTrue();
		var noContent = paths.path("/api/v1/admin/modifier-groups/{id}").path("delete").path("responses");
		assertThat(noContent.has("406")).isFalse();
		assertThat(noContent.has("415")).isFalse();
	}

	@Test
	void doesNotListValidationFailedForAnUnvalidatedPart() throws IOException {
		var upload = new JsonMapper().readTree(generate())
			.path("paths")
			.path("/api/v1/admin/products/{id}/image")
			.path("put")
			.path("responses");

		assertThat(codes(upload.path("400"))).doesNotContain("common.validation-failed");
	}

	@Test
	void documentsTheConditionalGetOnlyWhereTheETagIsExplicit() throws IOException {
		var paths = new JsonMapper().readTree(generate()).path("paths");

		var product = paths.path("/api/v1/admin/products/{id}").path("get");
		assertThat(product.path("responses").path("200").path("headers").has("ETag")).isTrue();
		assertThat(product.path("responses").has("304")).isFalse();
		product.path("parameters")
			.forEach(parameter -> assertThat(parameter.path("name").asString()).isNotEqualTo("If-None-Match"));
		assertThat(paths.path("/api/v1/admin/modifier-groups/{id}").path("get").path("responses").has("304")).isFalse();
	}

	@Test
	void documentsTheMenuConditionalRequest() throws IOException {
		var menu = new JsonMapper().readTree(generate()).path("paths").path("/api/v1/catalog/menu").path("get");

		assertThat(menu.path("responses").has("304")).isTrue();
		assertThat(menu.path("responses").path("200").path("headers").has("ETag")).isTrue();
		var ifNoneMatch = new ArrayList<JsonNode>();
		menu.path("parameters").forEach(parameter -> {
			if (parameter.path("name").asString().equals("If-None-Match")) {
				ifNoneMatch.add(parameter);
			}
		});
		assertThat(ifNoneMatch).singleElement().satisfies(parameter -> {
			assertThat(parameter.path("in").asString()).isEqualTo("header");
			assertThat(parameter.path("required").asBoolean()).isFalse();
		});
	}

	@Test
	void documentsTheLocationAndETagHeadersTheControllersSet() throws IOException {
		var paths = new JsonMapper().readTree(generate()).path("paths");

		var created = paths.path("/api/v1/admin/categories").path("post").path("responses").path("201").path("headers");
		assertThat(created.has("Location")).isTrue();
		assertThat(created.has("ETag")).isTrue();
		var changed = paths.path("/api/v1/admin/categories/{id}").path("put").path("responses").path("200").path("headers");
		assertThat(changed.has("ETag")).isTrue();
		assertThat(changed.has("Location")).isFalse();
		var staff = paths.path("/api/v1/staff").path("post").path("responses").path("201").path("headers");
		assertThat(staff.has("Location")).isTrue();
		assertThat(staff.has("ETag")).isFalse();
		var settings = paths.path("/api/v1/admin/settings").path("get").path("responses");
		assertThat(settings.path("200").path("headers").has("ETag")).isTrue();
		assertThat(settings.has("304")).isTrue();
		var hours = paths.path("/api/v1/admin/settings/opening-hours").path("get").path("responses");
		assertThat(hours.path("200").path("headers").has("ETag")).isTrue();
		var deleted = paths.path("/api/v1/admin/modifier-groups/{id}").path("delete").path("responses").path("204");
		assertThat(deleted.has("headers")).isFalse();
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
