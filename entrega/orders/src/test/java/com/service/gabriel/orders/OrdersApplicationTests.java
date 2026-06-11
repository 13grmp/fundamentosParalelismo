package com.service.gabriel.orders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.service.gabriel.orders.model.Role;
import com.service.gabriel.orders.repository.JsonOrderRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
		"storage.orders.file=target/test-data/orders-service-test.json",
		"jwt.secret=test-secret"
})
@AutoConfigureMockMvc
class OrdersApplicationTests {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Set<String> EXISTING_PRODUCTS = ConcurrentHashMap.newKeySet();
	private static HttpServer productServer;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JsonOrderRepository orderRepository;

	@DynamicPropertySource
	static void productServiceProperties(DynamicPropertyRegistry registry) {
		startProductServer();
		registry.add("services.products.url", () -> "http://localhost:" + productServer.getAddress().getPort());
	}

	@BeforeEach
	void cleanStorage() {
		orderRepository.deleteAll();
		EXISTING_PRODUCTS.clear();
		EXISTING_PRODUCTS.add("product-1");
	}

	@AfterAll
	static void stopProductServer() {
		if (productServer != null) {
			productServer.stop(0);
		}
	}

	@Test
	void createOrderLinksAuthenticatedUserAndExistingProduct() throws Exception {
		MvcResult result = mockMvc.perform(post("/orders")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken("user-1"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderJson("product-1", 2)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.userId").value("user-1"))
				.andExpect(jsonPath("$.data.productId").value("product-1"))
				.andExpect(jsonPath("$.data.quantity").value(2))
				.andExpect(jsonPath("$.data.status").value("created"))
				.andReturn();

		JsonNode order = read(result).get("data");
		mockMvc.perform(get("/orders/{userId}", "user-1")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken("user-1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].id").value(order.get("id").asString()));
	}

	@Test
	void createOrderRejectsMissingProduct() throws Exception {
		mockMvc.perform(post("/orders")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken("user-1"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderJson("missing-product", 1)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Produto nao encontrado"));
	}

	@Test
	void createOrderRejectsInvalidToken() throws Exception {
		mockMvc.perform(post("/orders")
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido")
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderJson("product-1", 1)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void commonUserCannotListAnotherUserOrders() throws Exception {
		mockMvc.perform(post("/orders")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken("user-2"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderJson("product-1", 1)))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/orders/{userId}", "user-2")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken("user-1")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Usuario comum so pode listar seus proprios pedidos"));
	}

	@Test
	void adminCanListAnyUserOrders() throws Exception {
		mockMvc.perform(post("/orders")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken("user-2"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(orderJson("product-1", 1)))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/orders/{userId}", "user-2")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.length()").value(1));
	}

	private static void startProductServer() {
		if (productServer != null) {
			return;
		}

		try {
			productServer = HttpServer.create(new InetSocketAddress(0), 0);
			productServer.createContext("/products", exchange -> {
				String productId = exchange.getRequestURI().getPath().replaceFirst("^/products/", "");
				boolean exists = EXISTING_PRODUCTS.contains(productId);
				byte[] body = (exists ? "{\"success\":true}" : "{\"success\":false}").getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(exists ? 200 : 404, body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
			});
			productServer.start();
		} catch (IOException ex) {
			throw new IllegalStateException("Nao foi possivel iniciar servidor de produtos de teste", ex);
		}
	}

	private String orderJson(String productId, int quantity) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
				"productId", productId,
				"quantity", quantity));
	}

	private String userToken(String userId) {
		return token(userId, Role.USER);
	}

	private String adminToken() {
		return token("admin-1", Role.ADMIN);
	}

	private String token(String userId, Role role) {
		try {
			Map<String, Object> header = new LinkedHashMap<>();
			header.put("alg", "HS256");
			header.put("typ", "JWT");

			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("userId", userId);
			payload.put("email", userId + "@example.com");
			payload.put("role", role.name().toLowerCase());
			payload.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());

			String encodedHeader = encodeJson(header);
			String encodedPayload = encodeJson(payload);
			String unsignedToken = encodedHeader + "." + encodedPayload;
			return unsignedToken + "." + sign(unsignedToken);
		} catch (Exception ex) {
			throw new IllegalStateException("Nao foi possivel gerar token de teste", ex);
		}
	}

	private String encodeJson(Map<String, Object> values) throws Exception {
		String json = objectMapper.writeValueAsString(values);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	private String sign(String value) throws Exception {
		Mac mac = Mac.getInstance(HMAC_ALGORITHM);
		mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
	}

	private JsonNode read(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}
}
