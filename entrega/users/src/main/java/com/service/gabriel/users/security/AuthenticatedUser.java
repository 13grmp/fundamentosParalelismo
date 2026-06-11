package com.service.gabriel.users.security;

import com.service.gabriel.users.model.Role;

public record AuthenticatedUser(String userId, String email, Role role) {

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}
}
