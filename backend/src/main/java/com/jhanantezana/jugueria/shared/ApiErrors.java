package com.jhanantezana.jugueria.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Wire codes ({@link ErrorCode#code()}) an endpoint can return beyond the ones every endpoint of its kind
 * shares (authentication, validation, If-Match). They are listed in the generated OpenAPI spec, and an unknown
 * code fails the spec generation.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApiErrors {

	String[] value();

}
