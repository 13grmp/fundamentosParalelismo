package com.service.gabriel.orders.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
		@NotBlank(message = "Produto e obrigatorio")
		String productId,
		@Min(value = 1, message = "Quantidade deve ser maior que zero")
		int quantity) {
}
