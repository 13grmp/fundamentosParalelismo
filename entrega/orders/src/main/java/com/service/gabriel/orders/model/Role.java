package com.service.gabriel.orders.model;

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
