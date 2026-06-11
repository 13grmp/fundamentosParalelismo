package com.service.gabriel.orders.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.service.gabriel.orders.client.ProductClient;
import com.service.gabriel.orders.dto.CreateOrderRequest;
import com.service.gabriel.orders.dto.OrderResponse;
import com.service.gabriel.orders.model.Order;
import com.service.gabriel.orders.model.OrderStatus;
import com.service.gabriel.orders.repository.JsonOrderRepository;
import com.service.gabriel.orders.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

	private final JsonOrderRepository orderRepository;
	private final ProductClient productClient;

	public OrderService(JsonOrderRepository orderRepository, ProductClient productClient) {
		this.orderRepository = orderRepository;
		this.productClient = productClient;
	}

	public OrderResponse create(CreateOrderRequest request, AuthenticatedUser authenticatedUser, String authorizationHeader) {
		if (!productClient.productExists(request.productId(), authorizationHeader)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto nao encontrado");
		}

		Order order = new Order(
				UUID.randomUUID().toString(),
				authenticatedUser.userId(),
				request.productId().trim(),
				request.quantity(),
				OrderStatus.CREATED,
				Instant.now());

		return OrderResponse.from(orderRepository.save(order));
	}

	public List<OrderResponse> listByUserId(String userId, AuthenticatedUser authenticatedUser) {
		if (!authenticatedUser.isAdmin() && !authenticatedUser.userId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario comum so pode listar seus proprios pedidos");
		}

		return orderRepository.findByUserId(userId).stream()
				.map(OrderResponse::from)
				.toList();
	}
}
