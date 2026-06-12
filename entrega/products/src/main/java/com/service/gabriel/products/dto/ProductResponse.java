package com.service.gabriel.products.dto;

import java.math.BigDecimal;

import com.service.gabriel.products.model.Product;

public record ProductResponse(String id, String name, String description, BigDecimal price, int stock) {

	public static ProductResponse from(Product product) {
		return new ProductResponse(
				product.getId(),
				product.getName(),
				product.getDescription(),
				product.getPrice(),
				product.getStock());
	}
}
