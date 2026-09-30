package com.jhanantezana.jugueria.identity.web;

import com.jhanantezana.jugueria.shared.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record CreateStaffRequest(@NotBlank @Email String email, @NotNull Role role) {
}
