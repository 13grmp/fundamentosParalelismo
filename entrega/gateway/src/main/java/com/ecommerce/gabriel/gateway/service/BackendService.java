package com.ecommerce.gabriel.gateway.service;

public enum BackendService {
	USERS("users", "/users"),
	PRODUCTS("products", "/products"),
	ORDERS("orders", "/orders");

	private final String id;
	private final String pathPrefix;

	BackendService(String id, String pathPrefix) {
		this.id = id;
		this.pathPrefix = pathPrefix;
	}

	public String id() {
		return id;
	}

	public String pathPrefix() {
		return pathPrefix;
	}
}
