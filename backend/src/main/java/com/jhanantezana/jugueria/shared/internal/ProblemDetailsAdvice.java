package com.jhanantezana.jugueria.shared.internal;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.jhanantezana.jugueria.shared.BusinessException;
import com.jhanantezana.jugueria.shared.CommonError;
import com.jhanantezana.jugueria.shared.CorrelationId;
import com.jhanantezana.jugueria.shared.ErrorCode;

@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

	record Violation(@Nullable String field, @Nullable String constraint, @Nullable String message) {
	}

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<Object> handleBusiness(BusinessException ex, WebRequest request) {
		var body = problem(ex.errorCode(), ex.getMessage());
		ex.properties().forEach(body::setProperty);
		return handleExceptionInternal(ex, body, new HttpHeaders(), ex.errorCode().status(), request);
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<Object> handleOptimisticLock(OptimisticLockingFailureException ex, WebRequest request) {
		var code = CommonError.CONCURRENT_MODIFICATION;
		return handleExceptionInternal(ex, problem(code, "The resource was modified concurrently"), new HttpHeaders(),
				code.status(), request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) throws Exception {
		// Spring Security translates these into 401 or 403 once they leave the dispatcher.
		if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
			throw ex;
		}
		var code = CommonError.INTERNAL_ERROR;
		return handleExceptionInternal(ex, problem(code, "Unexpected error"), new HttpHeaders(), code.status(),
				request);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var violations = Stream.concat(ex.getFieldErrors().stream(), ex.getGlobalErrors().stream())
			.map(ProblemDetailsAdvice::violation)
			.toList();
		return handleExceptionInternal(ex, validationProblem(ex.getBody(), violations), headers, status, request);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var violations = ex.getParameterValidationResults()
			.stream()
			.flatMap(ProblemDetailsAdvice::violations)
			.toList();
		return handleExceptionInternal(ex, validationProblem(ex.getBody(), violations), headers, status, request);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var response = super.handleExceptionInternal(ex, body, headers, status, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			var properties = problem.getProperties();
			if (properties == null || !properties.containsKey("code")) {
				problem.setProperty("code", fallbackCode(status).code());
			}
			problem.setProperty("correlationId", CorrelationId.current().orElse(null));
			logProblem(ex, problem, status);
		}
		return response;
	}

	private static ProblemDetail problem(ErrorCode code, @Nullable String detail) {
		var body = ProblemDetail.forStatusAndDetail(code.status(), detail);
		body.setProperty("code", code.code());
		return body;
	}

	private static ProblemDetail validationProblem(ProblemDetail body, List<Violation> violations) {
		body.setProperty("code", CommonError.VALIDATION_FAILED.code());
		body.setProperty("errors", violations);
		return body;
	}

	private static Stream<Violation> violations(ParameterValidationResult result) {
		if (result instanceof ParameterErrors errors) {
			return errors.getAllErrors().stream().map(ProblemDetailsAdvice::violation);
		}
		var parameter = result.getMethodParameter().getParameterName();
		return result.getResolvableErrors()
			.stream()
			.map(error -> new Violation(parameter, constraint(error), error.getDefaultMessage()));
	}

	private static Violation violation(ObjectError error) {
		var field = error instanceof FieldError fieldError ? fieldError.getField() : null;
		return new Violation(field, error.getCode(), error.getDefaultMessage());
	}

	// The most generic resolvable code is the bare constraint name, e.g. "Positive".
	private static @Nullable String constraint(MessageSourceResolvable error) {
		var codes = error.getCodes();
		return codes == null || codes.length == 0 ? null : Arrays.asList(codes).getLast();
	}

	private static ErrorCode fallbackCode(HttpStatusCode status) {
		return Arrays.stream(CommonError.values())
			.filter(code -> code.status().value() == status.value() && code != CommonError.VALIDATION_FAILED
					&& code != CommonError.CONCURRENT_MODIFICATION)
			.findFirst()
			.orElse(status.is4xxClientError() ? CommonError.MALFORMED_REQUEST : CommonError.INTERNAL_ERROR);
	}

	private static void logProblem(Exception ex, ProblemDetail problem, HttpStatusCode status) {
		var code = problem.getProperties() == null ? null : problem.getProperties().get("code");
		if (status.is5xxServerError()) {
			log.error("Request failed with {} {}", status.value(), code, ex);
		}
		else {
			// The exception message can echo client input, such as rejected field values.
			log.info("Request rejected with {} {} ({})", status.value(), code, ex.getClass().getSimpleName());
		}
	}

}
