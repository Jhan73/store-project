package com.jhanantezana.jugueria.shared;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

	String code();

	HttpStatus status();

}
