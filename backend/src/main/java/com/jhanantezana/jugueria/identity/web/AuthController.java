package com.jhanantezana.jugueria.identity.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhanantezana.jugueria.identity.internal.LoginService;

import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final LoginService loginService;

	AuthController(LoginService loginService) {
		this.loginService = loginService;
	}

	@PostMapping("/login")
	@PermitAll
	LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return LoginResponse.from(loginService.login(request.email(), request.password()));
	}

}
