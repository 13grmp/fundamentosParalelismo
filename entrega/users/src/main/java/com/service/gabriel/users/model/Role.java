package com.service.gabriel.users.model;

import java.util.Locale;

public enum Role {
	USER,
	ADMIN;

	public String tokenValue() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Role fromTokenValue(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Role ausente");
		}
		return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
	}
}
