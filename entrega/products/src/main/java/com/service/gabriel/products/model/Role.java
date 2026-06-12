package com.service.gabriel.products.model;

import java.util.Locale;

public enum Role {
	USER,
	ADMIN;

	public static Role fromTokenValue(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Role ausente");
		}
		return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
	}
}
