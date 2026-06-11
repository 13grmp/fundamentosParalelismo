package com.service.gabriel.users.service;

import java.util.Locale;
import java.util.UUID;

import com.service.gabriel.users.dto.LoginRequest;
import com.service.gabriel.users.dto.LoginResponse;
import com.service.gabriel.users.dto.RegisterRequest;
import com.service.gabriel.users.dto.UserResponse;
import com.service.gabriel.users.model.Role;
import com.service.gabriel.users.model.User;
import com.service.gabriel.users.repository.JsonUserRepository;
import com.service.gabriel.users.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

	private final JsonUserRepository userRepository;
	private final PasswordService passwordService;
	private final JwtService jwtService;

	public UserService(JsonUserRepository userRepository, PasswordService passwordService, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordService = passwordService;
		this.jwtService = jwtService;
	}

	public UserResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado");
		}

		User user = createUser(request.name().trim(), email, request.password(), Role.USER);
		return UserResponse.from(userRepository.save(user));
	}

	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas"));

		if (!passwordService.matches(request.password(), user.getPasswordSalt(), user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas");
		}

		return new LoginResponse(jwtService.createToken(user), UserResponse.from(user));
	}

	public UserResponse getById(String id, AuthenticatedUser authenticatedUser) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado"));

		if (!authenticatedUser.isAdmin() && !authenticatedUser.userId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario comum so pode consultar seus proprios dados");
		}

		return UserResponse.from(user);
	}

	public void createAdminIfMissing(String name, String email, String password) {
		String normalizedEmail = normalizeEmail(email);
		if (userRepository.existsByEmail(normalizedEmail)) {
			return;
		}
		userRepository.save(createUser(name.trim(), normalizedEmail, password, Role.ADMIN));
	}

	private User createUser(String name, String email, String password, Role role) {
		String salt = passwordService.generateSalt();
		return new User(
				UUID.randomUUID().toString(),
				name,
				email,
				passwordService.hash(password, salt),
				salt,
				role);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
