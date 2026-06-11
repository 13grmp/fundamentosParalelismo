package com.service.gabriel.orders.dto;

import java.time.Instant;

import com.service.gabriel.orders.model.Order;

public record OrderResponse(
		String id,
		String userId,
		String productId,
		int quantity,
		String status,
		Instant createdAt) {

	public static OrderResponse from(Order order) {
		return new OrderResponse(
				order.getId(),
				order.getUserId(),
				order.getProductId(),
				order.getQuantity(),
				order.getStatus().name().toLowerCase(),
				order.getCreatedAt());
	}
}
