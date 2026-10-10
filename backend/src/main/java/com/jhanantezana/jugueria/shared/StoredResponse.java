package com.jhanantezana.jugueria.shared;

/** The outcome of a command as the client first saw it; an empty body is an empty string. */
public record StoredResponse(int status, String body) {
}
