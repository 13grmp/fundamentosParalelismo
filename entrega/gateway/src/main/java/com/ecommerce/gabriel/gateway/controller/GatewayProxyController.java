package com.ecommerce.gabriel.gateway.controller;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ecommerce.gabriel.gateway.dto.ApiResponse;
import com.ecommerce.gabriel.gateway.service.BackendService;
import com.ecommerce.gabriel.gateway.service.ServiceRegistry;
import com.ecommerce.gabriel.gateway.service.ServiceState;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayProxyController {

	private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
			"connection",
			"content-length",
			"host",
			"transfer-encoding",
			"upgrade");

	private final ServiceRegistry serviceRegistry;
	private final HttpClient httpClient;

	public GatewayProxyController(ServiceRegistry serviceRegistry) {
		this.serviceRegistry = serviceRegistry;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(3))
				.build();
	}

	@RequestMapping("/users/**")
	public ResponseEntity<?> users(HttpServletRequest request) {
		return proxy(BackendService.USERS, request);
	}

	@RequestMapping("/products/**")
	public ResponseEntity<?> products(HttpServletRequest request) {
		return proxy(BackendService.PRODUCTS, request);
	}

	@RequestMapping("/orders/**")
	public ResponseEntity<?> orders(HttpServletRequest request) {
		return proxy(BackendService.ORDERS, request);
	}

	private ResponseEntity<?> proxy(BackendService backendService, HttpServletRequest servletRequest) {
		ServiceState state = serviceRegistry.get(backendService);
		if (!state.available()) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(ApiResponse.error("Servico " + backendService.id() + " indisponivel"));
		}

		try {
			HttpRequest outboundRequest = buildOutboundRequest(state, servletRequest);
			HttpResponse<byte[]> outboundResponse = httpClient.send(outboundRequest, HttpResponse.BodyHandlers.ofByteArray());
			return buildResponse(outboundResponse);
		} catch (IOException ex) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(ApiResponse.error("Servico " + backendService.id() + " indisponivel"));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(ApiResponse.error("Requisicao ao servico " + backendService.id() + " interrompida"));
		}
	}

	private HttpRequest buildOutboundRequest(ServiceState state, HttpServletRequest servletRequest) throws IOException {
		String targetUrl = state.baseUrl() + servletRequest.getRequestURI();
		if (servletRequest.getQueryString() != null) {
			targetUrl += "?" + servletRequest.getQueryString();
		}

		byte[] body = servletRequest.getInputStream().readAllBytes();
		HttpRequest.BodyPublisher bodyPublisher = body.length == 0
				? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofByteArray(body);

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(targetUrl))
				.timeout(Duration.ofSeconds(10))
				.method(servletRequest.getMethod(), bodyPublisher);

		servletRequest.getHeaderNames().asIterator().forEachRemaining(headerName -> {
			if (shouldForwardHeader(headerName)) {
				servletRequest.getHeaders(headerName).asIterator()
						.forEachRemaining(headerValue -> builder.header(headerName, headerValue));
			}
		});

		return builder.build();
	}

	private ResponseEntity<byte[]> buildResponse(HttpResponse<byte[]> outboundResponse) {
		HttpHeaders headers = new HttpHeaders();
		for (Map.Entry<String, List<String>> entry : outboundResponse.headers().map().entrySet()) {
			if (shouldForwardHeader(entry.getKey())) {
				headers.put(entry.getKey(), entry.getValue());
			}
		}
		return new ResponseEntity<>(outboundResponse.body(), headers, HttpStatus.valueOf(outboundResponse.statusCode()));
	}

	private boolean shouldForwardHeader(String headerName) {
		return !HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase());
	}
}
