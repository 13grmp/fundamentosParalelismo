package com.ecommerce.gabriel.gateway.service;

import java.time.Instant;

public class ServiceState {

	private final BackendService service;
	private final String baseUrl;
	private int consecutiveFailures;
	private boolean available = true;
	private Instant lastFailureAt;
	private Instant lastRecoveryAt;

	public ServiceState(BackendService service, String baseUrl) {
		this.service = service;
		this.baseUrl = normalizeBaseUrl(baseUrl);
	}

	public BackendService service() {
		return service;
	}

	public String baseUrl() {
		return baseUrl;
	}

	public int consecutiveFailures() {
		return consecutiveFailures;
	}

	public boolean available() {
		return available;
	}

	public Instant lastFailureAt() {
		return lastFailureAt;
	}

	public Instant lastRecoveryAt() {
		return lastRecoveryAt;
	}

	public void markSuccess() {
		consecutiveFailures = 0;
		if (!available) {
			lastRecoveryAt = Instant.now();
		}
		available = true;
	}

	public void markFailure(int maxFailures) {
		consecutiveFailures++;
		if (consecutiveFailures >= maxFailures) {
			if (available) {
				lastFailureAt = Instant.now();
			}
			available = false;
		}
	}

	private String normalizeBaseUrl(String value) {
		if (value.endsWith("/")) {
			return value.substring(0, value.length() - 1);
		}
		return value;
	}
}
