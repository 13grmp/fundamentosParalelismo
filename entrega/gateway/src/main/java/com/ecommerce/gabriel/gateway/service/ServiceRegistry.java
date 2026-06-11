package com.ecommerce.gabriel.gateway.service;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServiceRegistry {

	private final Map<BackendService, ServiceState> states = new EnumMap<>(BackendService.class);

	public ServiceRegistry(
			@Value("${services.users.url}") String usersUrl,
			@Value("${services.products.url}") String productsUrl,
			@Value("${services.orders.url}") String ordersUrl) {
		states.put(BackendService.USERS, new ServiceState(BackendService.USERS, usersUrl));
		states.put(BackendService.PRODUCTS, new ServiceState(BackendService.PRODUCTS, productsUrl));
		states.put(BackendService.ORDERS, new ServiceState(BackendService.ORDERS, ordersUrl));
	}

	public ServiceState get(BackendService service) {
		return states.get(service);
	}

	public Map<BackendService, ServiceState> all() {
		return Map.copyOf(states);
	}
}
