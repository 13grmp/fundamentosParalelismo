package com.service.gabriel.products.service;

import java.util.List;
import java.util.UUID;

import com.service.gabriel.products.dto.CreateProductRequest;
import com.service.gabriel.products.dto.ProductResponse;
import com.service.gabriel.products.model.Product;
import com.service.gabriel.products.repository.JsonProductRepository;
import com.service.gabriel.products.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

	private final JsonProductRepository productRepository;

	public ProductService(JsonProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	public List<ProductResponse> listAll() {
		return productRepository.findAllRoundRobin().stream()
				.map(ProductResponse::from)
				.toList();
	}

	public ProductResponse getById(String id) {
		return productRepository.findByIdRoundRobin(id)
				.map(ProductResponse::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto nao encontrado"));
	}

	public ProductResponse create(CreateProductRequest request, AuthenticatedUser authenticatedUser) {
		if (!authenticatedUser.isAdmin()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas admin pode criar produtos");
		}

		Product product = new Product(
				UUID.randomUUID().toString(),
				request.name().trim(),
				request.description().trim(),
				request.price(),
				request.stock());

		try {
			return ProductResponse.from(productRepository.saveToAllReplicas(product));
		} catch (RuntimeException ex) {
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Falha ao gravar produto em todas as replicas",
					ex);
		}
	}

	public ProductResponse reserveStock(String id, int quantity) {
		Product product = productRepository.findByIdPrimary(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto nao encontrado"));

		if (product.getStock() < quantity) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Estoque insuficiente");
		}

		product.setStock(product.getStock() - quantity);

		try {
			return ProductResponse.from(productRepository.saveToAllReplicas(product));
		} catch (RuntimeException ex) {
			throw new ResponseStatusException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Falha ao atualizar estoque em todas as replicas",
					ex);
		}
	}
}
