package com.service.gabriel.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.service.gabriel.products.model.Role;
import com.service.gabriel.products.repository.JsonProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
		"storage.products.primary-file=target/test-data/products-primary-test.json",
		"storage.products.replica-file=target/test-data/products-replica-test.json",
		"jwt.secret=test-secret"
})
@AutoConfigureMockMvc
class ProductsApplicationTests {

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JsonProductRepository productRepository;

	@BeforeEach
	void cleanStorage() {
		productRepository.deleteAll();
	}

	@Test
	void adminCanCreateProductAndWriteBothReplicas() throws Exception {
		JsonNode product = createProduct(adminToken(), "Notebook", "Notebook gamer", "3999.90", 7);

		assertThat(product.get("name").asString()).isEqualTo("Notebook");
		assertThat(product.get("price").decimalValue()).isEqualByComparingTo(new BigDecimal("3999.90"));

		String primaryContent = Files.readString(productRepository.primaryFile());
		String replicaContent = Files.readString(productRepository.replicaFile());
		assertThat(primaryContent).contains(product.get("id").asString());
		assertThat(replicaContent).contains(product.get("id").asString());
	}

	@Test
	void commonUserCannotCreateProduct() throws Exception {
		mockMvc.perform(post("/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken())
						.contentType(MediaType.APPLICATION_JSON)
						.content(productJson("Mouse", "Mouse sem fio", "99.90", 12)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Apenas admin pode criar produtos"));
	}

	@Test
	void listProductsReturnsCreatedProducts() throws Exception {
		createProduct(adminToken(), "Notebook", "Notebook gamer", "3999.90", 7);
		createProduct(adminToken(), "Mouse", "Mouse sem fio", "99.90", 12);

		mockMvc.perform(get("/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.length()").value(2));
	}

	@Test
	void getByIdReturnsProductDetails() throws Exception {
		JsonNode product = createProduct(adminToken(), "Teclado", "Teclado mecanico", "250.00", 5);

		mockMvc.perform(get("/products/{id}", product.get("id").asString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.name").value("Teclado"))
				.andExpect(jsonPath("$.data.stock").value(5));
	}

	@Test
	void createRejectsMissingToken() throws Exception {
		mockMvc.perform(post("/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content(productJson("Mouse", "Mouse sem fio", "99.90", 12)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false));
	}

	private JsonNode createProduct(String token, String name, String description, String price, int stock) throws Exception {
		MvcResult result = mockMvc.perform(post("/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(productJson(name, description, price, stock)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").isString())
				.andReturn();

		return read(result).get("data");
	}

	private String productJson(String name, String description, String price, int stock) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
				"name", name,
				"description", description,
				"price", price,
				"stock", stock));
	}

	private String adminToken() {
		return token(Role.ADMIN);
	}

	private String userToken() {
		return token(Role.USER);
	}

	private String token(Role role) {
		try {
			Map<String, Object> header = new LinkedHashMap<>();
			header.put("alg", "HS256");
			header.put("typ", "JWT");

			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("userId", "user-1");
			payload.put("email", "user@example.com");
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
