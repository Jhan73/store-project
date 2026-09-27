package com.jhanantezana.jugueria.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class SetPasswordRequestTest {

	static ValidatorFactory factory;

	static Validator validator;

	@BeforeAll
	static void createValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Ab1!efgh", "Jugo-De-Fresa-2026", "Aa1!aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" })
	void acceptsAPasswordThatMeetsThePolicy(String password) {
		assertThat(validator.validate(new SetPasswordRequest("token", password))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Ab1!efg",
			"Aa1!aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
			"ab1!efgh",
			"AB1!EFGH",
			"Abc!efgh",
			"Ab1cefgh" })
	void rejectsAPasswordThatBreaksThePolicy(String password) {
		assertThat(validator.validate(new SetPasswordRequest("token", password)))
			.extracting(violation -> violation.getPropertyPath().toString())
			.containsOnly("newPassword");
	}

}
