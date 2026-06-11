package com.service.gabriel.users.controller;

import com.service.gabriel.users.dto.ApiResponse;
import com.service.gabriel.users.dto.LoginRequest;
import com.service.gabriel.users.dto.LoginResponse;
import com.service.gabriel.users.dto.RegisterRequest;
import com.service.gabriel.users.dto.UserResponse;
import com.service.gabriel.users.security.AuthenticatedUser;
import com.service.gabriel.users.service.JwtService;
import com.service.gabriel.users.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;
	private final JwtService jwtService;

	public UserController(UserService userService, JwtService jwtService) {
		this.userService = userService;
		this.jwtService = jwtService;
	}

	@PostMapping("/register")
	public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
		UserResponse user = userService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok("Usuario criado com sucesso", user));
	}

	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.ok("Login realizado com sucesso", userService.login(request));
	}

	@GetMapping("/{id}")
	public ApiResponse<UserResponse> getById(
			@PathVariable String id,
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
		AuthenticatedUser authenticatedUser = jwtService.validateAuthorizationHeader(authorizationHeader);
		return ApiResponse.ok("Usuario encontrado", userService.getById(id, authenticatedUser));
	}
}
