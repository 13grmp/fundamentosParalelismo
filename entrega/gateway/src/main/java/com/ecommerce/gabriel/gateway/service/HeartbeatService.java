package com.ecommerce.gabriel.gateway.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HeartbeatService {

	private static final Logger logger = LoggerFactory.getLogger(HeartbeatService.class);

	private final ServiceRegistry serviceRegistry;
	private final HttpClient httpClient;
	private final int maxFailures;

	public HeartbeatService(
			ServiceRegistry serviceRegistry,
			@Value("${gateway.heartbeat.max-failures}") int maxFailures) {
		this.serviceRegistry = serviceRegistry;
		this.maxFailures = maxFailures;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(2))
				.build();
	}

	@Scheduled(fixedDelayString = "${gateway.heartbeat.interval-ms:5000}")
	public void checkAllServices() {
		serviceRegistry.all().values().forEach(this::checkService);
	}

	public void checkService(ServiceState state) {
		boolean wasAvailable = state.available();
		if (isHealthy(state)) {
			state.markSuccess();
			if (!wasAvailable && state.available()) {
				logger.info("Servico {} recuperado em {}", state.service().id(), state.lastRecoveryAt());
			}
			return;
		}

		state.markFailure(maxFailures);
		if (wasAvailable && !state.available()) {
			logger.warn("Servico {} indisponivel em {}", state.service().id(), state.lastFailureAt());
		}
	}

	private boolean isHealthy(ServiceState state) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(state.baseUrl() + "/health"))
				.timeout(Duration.ofSeconds(2))
				.GET()
				.build();

		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() >= 200 && response.statusCode() < 300;
		} catch (Exception ex) {
			return false;
		}
	}
}
