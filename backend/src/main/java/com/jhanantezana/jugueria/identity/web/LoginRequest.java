package com.jhanantezana.jugueria.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

record LoginRequest(@NotBlank @Email String email, @NotBlank @MaxUtf8Bytes(72) String password) {
}
