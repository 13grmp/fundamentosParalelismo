package com.service.gabriel.products.security;

import com.service.gabriel.products.model.Role;

public record AuthenticatedUser(String userId, String email, Role role) {

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}
}
