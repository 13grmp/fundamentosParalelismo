package com.service.gabriel.orders.client;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProductClient {

	private final HttpClient httpClient;
	private final String productsServiceUrl;

	public ProductClient(@Value("${services.products.url}") String productsServiceUrl) {
		this.productsServiceUrl = productsServiceUrl;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(2))
				.build();
	}

	public boolean productExists(String productId, String authorizationHeader) {
		String encodedId = URLEncoder.encode(productId, StandardCharsets.UTF_8);
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(productsServiceUrl + "/products/" + encodedId))
				.timeout(Duration.ofSeconds(3))
				.GET();

		if (authorizationHeader != null) {
			requestBuilder.header("Authorization", authorizationHeader);
		}

		try {
			HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				return true;
			}
			if (response.statusCode() == 404) {
				return false;
			}
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servico de produtos indisponivel");
		} catch (IOException ex) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servico de produtos indisponivel", ex);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta ao servico de produtos interrompida", ex);
		}
	}
}
