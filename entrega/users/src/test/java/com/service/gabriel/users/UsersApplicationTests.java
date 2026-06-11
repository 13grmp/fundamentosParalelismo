package com.service.gabriel.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.service.gabriel.users.model.Role;
import com.service.gabriel.users.model.User;
import com.service.gabriel.users.repository.JsonUserRepository;
import com.service.gabriel.users.security.AuthenticatedUser;
import com.service.gabriel.users.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
		"storage.users.file=target/test-data/users-service-test.json",
		"users.admin.seed.enabled=false",
		"jwt.secret=test-secret",
		"jwt.expiration-minutes=60"
})
@AutoConfigureMockMvc
class UsersApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JsonUserRepository userRepository;

	@Autowired
	private JwtService jwtService;

	@BeforeEach
	void cleanStorage() {
		userRepository.deleteAll();
	}

	@Test
	void registerCreatesUserWithHashedPassword() throws Exception {
		JsonNode data = register("Gabriel", "USER@example.com", "secret123");

		assertThat(data.get("email").asString()).isEqualTo("user@example.com");
		assertThat(data.get("role").asString()).isEqualTo("user");
		assertThat(data.get("passwordHash")).isNull();

		User savedUser = userRepository.findByEmail("user@example.com").orElseThrow();
		assertThat(savedUser.getPasswordHash()).isNotBlank();
		assertThat(savedUser.getPasswordHash()).isNotEqualTo("secret123");
		assertThat(savedUser.getPasswordSalt()).isNotBlank();
		assertThat(savedUser.getRole()).isEqualTo(Role.USER);
	}

	@Test
	void loginReturnsJwtWithRequiredClaims() throws Exception {
		JsonNode user = register("Gabriel", "gabriel@example.com", "secret123");

		MvcResult result = mockMvc.perform(post("/users/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of(
								"email", "gabriel@example.com",
								"password", "secret123"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.token").isString())
				.andExpect(jsonPath("$.data.user.email").value("gabriel@example.com"))
				.andReturn();

		String token = read(result).get("data").get("token").asString();
		assertThat(token.split("\\.")).hasSize(3);

		AuthenticatedUser authenticatedUser = jwtService.validateToken(token);
		assertThat(authenticatedUser.userId()).isEqualTo(user.get("id").asString());
		assertThat(authenticatedUser.email()).isEqualTo("gabriel@example.com");
		assertThat(authenticatedUser.role()).isEqualTo(Role.USER);
	}

	@Test
	void getByIdReturnsAuthenticatedUserWithoutPasswordData() throws Exception {
		JsonNode user = register("Gabriel", "gabriel@example.com", "secret123");
		String token = login("gabriel@example.com", "secret123");

		mockMvc.perform(get("/users/{id}", user.get("id").asString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.email").value("gabriel@example.com"))
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.data.passwordSalt").doesNotExist());
	}

	@Test
	void getByIdRejectsInvalidToken() throws Exception {
		JsonNode user = register("Gabriel", "gabriel@example.com", "secret123");

		mockMvc.perform(get("/users/{id}", user.get("id").asString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void commonUserCannotGetAnotherUserData() throws Exception {
		register("Gabriel", "gabriel@example.com", "secret123");
		JsonNode anotherUser = register("Outro", "outro@example.com", "secret123");
		String token = login("gabriel@example.com", "secret123");

		mockMvc.perform(get("/users/{id}", anotherUser.get("id").asString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.success").value(false));
	}

	private JsonNode register(String name, String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/users/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of(
								"name", name,
								"email", email,
								"password", password))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").isString())
				.andReturn();

		return read(result).get("data");
	}

	private String login(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/users/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of(
								"email", email,
								"password", password))))
				.andExpect(status().isOk())
				.andReturn();

		return read(result).get("data").get("token").asString();
	}

	private String json(Map<String, String> values) throws Exception {
		return objectMapper.writeValueAsString(values);
	}

	private JsonNode read(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}
}
