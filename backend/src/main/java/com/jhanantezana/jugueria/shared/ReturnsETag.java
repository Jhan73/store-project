package com.jhanantezana.jugueria.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler that sets an {@code ETag} the generated OpenAPI spec cannot infer. It is inferred for a
 * handler that takes {@code If-Match} or whose body has an {@code etag} component.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ReturnsETag {

}
