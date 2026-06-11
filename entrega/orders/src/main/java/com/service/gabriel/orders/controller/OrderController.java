package com.service.gabriel.orders.controller;

import java.util.List;

import com.service.gabriel.orders.dto.ApiResponse;
import com.service.gabriel.orders.dto.CreateOrderRequest;
import com.service.gabriel.orders.dto.OrderResponse;
import com.service.gabriel.orders.security.AuthenticatedUser;
import com.service.gabriel.orders.service.JwtService;
import com.service.gabriel.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/orders")
public class OrderController {

	private final OrderService orderService;
	private final JwtService jwtService;

	public OrderController(OrderService orderService, JwtService jwtService) {
		this.orderService = orderService;
		this.jwtService = jwtService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<OrderResponse>> create(
			@Valid @RequestBody CreateOrderRequest request,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
		AuthenticatedUser authenticatedUser = jwtService.validateAuthorizationHeader(authorizationHeader);
		OrderResponse order = orderService.create(request, authenticatedUser, authorizationHeader);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok("Pedido criado com sucesso", order));
	}

	@GetMapping("/{userId}")
	public ApiResponse<List<OrderResponse>> listByUserId(
			@PathVariable String userId,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
		AuthenticatedUser authenticatedUser = jwtService.validateAuthorizationHeader(authorizationHeader);
		return ApiResponse.ok("Pedidos encontrados", orderService.listByUserId(userId, authenticatedUser));
	}
}
