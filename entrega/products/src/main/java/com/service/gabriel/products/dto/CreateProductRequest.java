package com.service.gabriel.products.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProductRequest(
		@NotBlank(message = "Nome do produto e obrigatorio")
		String name,
		@NotBlank(message = "Descricao do produto e obrigatoria")
		String description,
		@NotNull(message = "Preco e obrigatorio")
		@DecimalMin(value = "0.01", message = "Preco deve ser maior que zero")
		BigDecimal price,
		@Min(value = 0, message = "Estoque nao pode ser negativo")
		int stock) {
}
