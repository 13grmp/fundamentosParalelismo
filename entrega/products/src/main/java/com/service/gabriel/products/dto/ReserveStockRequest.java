package com.service.gabriel.products.dto;

import jakarta.validation.constraints.Min;

public record ReserveStockRequest(
		@Min(value = 1, message = "Quantidade deve ser maior que zero")
		int quantity) {
}
