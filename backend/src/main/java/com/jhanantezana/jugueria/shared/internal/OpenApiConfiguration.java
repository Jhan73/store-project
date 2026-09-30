package com.jhanantezana.jugueria.shared.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.method.HandlerMethod;

import com.jhanantezana.jugueria.shared.ApiErrors;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.ErrorCode;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import jakarta.annotation.security.PermitAll;

// Shapes the generated spec only; the app never serves it (springdoc is enabled by the spec test alone).
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

	private static final String BEARER = "bearerAuth";

	private static final String PROBLEM = "Problem";

	private static final String ERROR_CODE = "ErrorCode";

	private final Map<String, ErrorCode> codes = declaredCodes();

	@Bean
	OpenAPI openApi() {
		return new OpenAPI().info(new Info().title("Jugueria API").version("v1"))
			// A fixed relative server keeps the file identical whichever host generated it.
			.servers(List.of(new Server().url("/")))
			.components(new Components().addSecuritySchemes(BEARER,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
	}

	@Bean
	OpenApiCustomizer problemSchemas() {
		return openApi -> {
			var components = openApi.getComponents();
			components.addSchemas(ERROR_CODE, new StringSchema()._enum(codes.keySet().stream().sorted().toList())
				.description("Every error code the API can return; codes are never renamed or reused."));
			components.addSchemas(PROBLEM, problem());
			if (openApi.getTags() != null) {
				openApi.setTags(openApi.getTags().stream().sorted(Comparator.comparing(Tag::getName)).toList());
			}
		};
	}

	@Bean
	OperationCustomizer standardResponses() {
		return (operation, handler) -> {
			operation.setOperationId(operationId(handler));
			var errors = new LinkedHashSet<ErrorCode>();
			if (!handler.hasMethodAnnotation(PermitAll.class)) {
				operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
				errors.add(declared("auth.unauthenticated"));
				if (handler.hasMethodAnnotation(PreAuthorize.class)) {
					errors.add(declared("auth.forbidden"));
				}
			}
			addInputErrors(handler, errors);
			var declared = handler.getMethodAnnotation(ApiErrors.class);
			if (declared != null) {
				Arrays.stream(declared.value()).map(this::declared).forEach(errors::add);
			}
			errors.add(CommonError.INTERNAL_ERROR);
			addProblemResponses(operation, errors);
			return operation;
		};
	}

	// Named after the controller and method: springdoc's own numeric suffixes depend on scan order.
	private static String operationId(HandlerMethod handler) {
		var controller = handler.getBeanType().getSimpleName().replaceFirst("Controller$", "");
		var method = handler.getMethod().getName();
		return Character.toLowerCase(controller.charAt(0)) + controller.substring(1)
				+ Character.toUpperCase(method.charAt(0)) + method.substring(1);
	}

	private static void addInputErrors(HandlerMethod handler, Set<ErrorCode> errors) {
		for (var parameter : handler.getMethodParameters()) {
			if (parameter.hasParameterAnnotation(RequestHeader.class)
					&& parameter.getParameterAnnotation(RequestHeader.class).value().equalsIgnoreCase("If-Match")) {
				errors.add(CommonError.PRECONDITION_REQUIRED);
				errors.add(CommonError.PRECONDITION_FAILED);
				errors.add(CommonError.CONCURRENT_MODIFICATION);
				errors.add(CommonError.MALFORMED_REQUEST);
			}
			else if (parameter.hasParameterAnnotation(RequestBody.class)
					|| parameter.hasParameterAnnotation(RequestPart.class)
					|| parameter.hasParameterAnnotation(RequestParam.class)
					|| parameter.hasParameterAnnotation(PathVariable.class)) {
				errors.add(CommonError.VALIDATION_FAILED);
				errors.add(CommonError.MALFORMED_REQUEST);
			}
			else if (parameter.hasParameterAnnotation(RequestHeader.class)
					|| parameter.hasParameterAnnotation(CookieValue.class)) {
				errors.add(CommonError.MALFORMED_REQUEST);
			}
		}
	}

	private ErrorCode declared(String code) {
		var errorCode = codes.get(code);
		if (errorCode == null) {
			throw new IllegalStateException("@ApiErrors names an unknown error code: " + code);
		}
		return errorCode;
	}

	private static void addProblemResponses(Operation operation, Set<ErrorCode> errors) {
		var byStatus = new TreeMap<Integer, List<String>>();
		errors.forEach(error -> byStatus.computeIfAbsent(error.status().value(), status -> new ArrayList<>())
			.add(error.code()));
		if (operation.getResponses() == null) {
			operation.setResponses(new ApiResponses());
		}
		byStatus.forEach((status, codes) -> {
			var sorted = codes.stream().sorted().toList();
			var response = new ApiResponse()
				.description(HttpStatus.valueOf(status).getReasonPhrase() + ". Error codes: " + String.join(", ", sorted))
				.content(new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
						new io.swagger.v3.oas.models.media.MediaType()
							.schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))));
			response.addExtension("x-error-codes", sorted);
			operation.getResponses().addApiResponse(String.valueOf(status), response);
		});
	}

	private static Schema<?> problem() {
		var errors = new ObjectSchema().addProperty("field", new StringSchema().nullable(true))
			.addProperty("constraint", new StringSchema().nullable(true))
			.addProperty("message", new StringSchema().nullable(true));
		var problem = new ObjectSchema().description("RFC 9457 Problem Details; extra members depend on the code.")
			.addProperty("type", new StringSchema().format("uri"))
			.addProperty("title", new StringSchema())
			.addProperty("status", new IntegerSchema())
			.addProperty("detail", new StringSchema())
			.addProperty("instance", new StringSchema().format("uri"))
			.addProperty("code", new Schema<>().$ref("#/components/schemas/" + ERROR_CODE))
			.addProperty("correlationId", new StringSchema().nullable(true))
			.addProperty("errors", new ArraySchema().items(errors));
		problem.setRequired(List.of("code", "status"));
		problem.setAdditionalProperties(true);
		return problem;
	}

	private static Map<String, ErrorCode> declaredCodes() {
		var scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));
		var codes = new LinkedHashMap<String, ErrorCode>();
		for (var candidate : scanner.findCandidateComponents("com.jhanantezana.jugueria")) {
			try {
				var type = Class.forName(candidate.getBeanClassName());
				if (type.isEnum()) {
					for (var constant : type.getEnumConstants()) {
						var errorCode = (ErrorCode) constant;
						codes.put(errorCode.code(), errorCode);
					}
				}
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalStateException(ex);
			}
		}
		return codes;
	}

}
