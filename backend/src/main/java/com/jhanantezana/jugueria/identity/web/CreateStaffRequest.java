package com.jhanantezana.jugueria.identity.web;

import com.jhanantezana.jugueria.shared.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record CreateStaffRequest(@NotBlank @Size(max = 60) String username, @NotBlank @Email String email,
		@NotNull Role role) {
}
