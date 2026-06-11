package com.service.gabriel.orders.security;

import com.service.gabriel.orders.model.Role;

public record AuthenticatedUser(String userId, String email, Role role) {

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}
}
