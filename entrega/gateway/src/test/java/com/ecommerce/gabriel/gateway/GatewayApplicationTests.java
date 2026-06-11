package com.ecommerce.gabriel.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ecommerce.gabriel.gateway.service.HeartbeatService;
import com.ecommerce.gabriel.gateway.service.ServiceRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"gateway.heartbeat.interval-ms=600000",
		"gateway.heartbeat.max-failures=1"
})
@AutoConfigureMockMvc
class GatewayApplicationTests {

	private static final Map<String, MockBackend> BACKENDS = new ConcurrentHashMap<>();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HeartbeatService heartbeatService;

	@Autowired
	private ServiceRegistry serviceRegistry;

	@DynamicPropertySource
	static void backendProperties(DynamicPropertyRegistry registry) {
		startBackends();
		registry.add("services.users.url", () -> BACKENDS.get("users").baseUrl());
		registry.add("services.products.url", () -> BACKENDS.get("products").baseUrl());
		registry.add("services.orders.url", () -> BACKENDS.get("orders").baseUrl());
	}

	@BeforeEach
	void resetBackends() {
		BACKENDS.values().forEach(MockBackend::reset);
		heartbeatService.checkAllServices();
	}

	@AfterAll
	static void stopBackends() {
		BACKENDS.values().forEach(MockBackend::stop);
	}

	@Test
	void proxiesUsersRouteAndForwardsAuthorizationHeader() throws Exception {
		mockMvc.perform(post("/users/register")
						.header(HttpHeaders.AUTHORIZATION, "Bearer abc")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Gabriel\"}"))
				.andExpect(status().isCreated())
				.andExpect(content().json("{\"service\":\"users\",\"path\":\"/users/register\"}"));

		MockBackend users = BACKENDS.get("users");
		assertThat(users.lastAuthorization()).isEqualTo("Bearer abc");
		assertThat(users.lastBody()).isEqualTo("{\"name\":\"Gabriel\"}");
	}

	@Test
	void proxiesProductsAndOrdersWithoutCouplingServiceStates() throws Exception {
		mockMvc.perform(get("/products/product-1"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"service\":\"products\",\"path\":\"/products/product-1\"}"));

		mockMvc.perform(get("/orders/user-1"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"service\":\"orders\",\"path\":\"/orders/user-1\"}"));
	}

	@Test
	void returnsUnavailableWhenRequestedServiceFailsHeartbeat() throws Exception {
		BACKENDS.get("orders").setHealthy(false);
		heartbeatService.checkAllServices();

		mockMvc.perform(get("/orders/user-1"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Servico orders indisponivel"));

		mockMvc.perform(get("/users/user-1"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"service\":\"users\",\"path\":\"/users/user-1\"}"));
	}

	@Test
	void heartbeatRegistersRecoveryAfterServiceComesBack() throws Exception {
		BACKENDS.get("orders").setHealthy(false);
		heartbeatService.checkAllServices();
		assertThat(serviceRegistry.get(com.ecommerce.gabriel.gateway.service.BackendService.ORDERS).available()).isFalse();

		BACKENDS.get("orders").setHealthy(true);
		heartbeatService.checkAllServices();
		assertThat(serviceRegistry.get(com.ecommerce.gabriel.gateway.service.BackendService.ORDERS).available()).isTrue();

		mockMvc.perform(get("/orders/user-1"))
				.andExpect(status().isOk())
				.andExpect(content().json("{\"service\":\"orders\",\"path\":\"/orders/user-1\"}"));
	}

	private static void startBackends() {
		BACKENDS.computeIfAbsent("users", MockBackend::new);
		BACKENDS.computeIfAbsent("products", MockBackend::new);
		BACKENDS.computeIfAbsent("orders", MockBackend::new);
	}

	private static final class MockBackend {

		private final String name;
		private final HttpServer server;
		private volatile boolean healthy = true;
		private volatile String lastAuthorization;
		private volatile String lastBody;

		private MockBackend(String name) {
			this.name = name;
			try {
				this.server = HttpServer.create(new InetSocketAddress(0), 0);
				this.server.createContext("/health", this::handleHealth);
				this.server.createContext("/", this::handleProxy);
				this.server.start();
			} catch (IOException ex) {
				throw new IllegalStateException("Nao foi possivel iniciar backend de teste " + name, ex);
			}
		}

		private String baseUrl() {
			return "http://localhost:" + server.getAddress().getPort();
		}

		private String lastAuthorization() {
			return lastAuthorization;
		}

		private String lastBody() {
			return lastBody;
		}

		private void setHealthy(boolean healthy) {
			this.healthy = healthy;
		}

		private void reset() {
			healthy = true;
			lastAuthorization = null;
			lastBody = null;
		}

		private void stop() {
			server.stop(0);
		}

		private void handleHealth(HttpExchange exchange) throws IOException {
			byte[] body = (healthy ? "{\"status\":\"ok\"}" : "{\"status\":\"down\"}").getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(healthy ? 200 : 500, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		}

		private void handleProxy(HttpExchange exchange) throws IOException {
			lastAuthorization = exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
			lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String path = exchange.getRequestURI().getPath();
			byte[] body = ("{\"service\":\"" + name + "\",\"path\":\"" + path + "\"}").getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			exchange.sendResponseHeaders("POST".equals(exchange.getRequestMethod()) ? 201 : 200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		}
	}
}
