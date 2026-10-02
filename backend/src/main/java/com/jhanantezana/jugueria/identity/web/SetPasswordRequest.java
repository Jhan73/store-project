package com.jhanantezana.jugueria.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record SetPasswordRequest(@NotBlank String token,
		@NotBlank @Size(min = 8, max = 50) @MaxUtf8Bytes(72)
		@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*$") String newPassword) {
}
