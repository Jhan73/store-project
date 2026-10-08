package com.jhanantezana.jugueria.identity.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

// bcrypt cannot hash more than 72 bytes, and the encoder throws instead of truncating, so a longer value would be a 500.
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

	int value();

	String message() default "must be at most {value} bytes in UTF-8";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
