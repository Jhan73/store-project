package com.jhanantezana.jugueria.identity.web;

import com.jhanantezana.jugueria.shared.Role;

import jakarta.validation.constraints.NotNull;

record ChangeStaffRoleRequest(@NotNull Role role) {
}
