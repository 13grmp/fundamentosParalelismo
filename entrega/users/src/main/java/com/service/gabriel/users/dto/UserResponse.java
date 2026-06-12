package com.service.gabriel.users.dto;

import com.service.gabriel.users.model.User;

public record UserResponse(String id, String name, String email, String role) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getRole().tokenValue());
	}
}
