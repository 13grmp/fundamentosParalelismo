package com.service.gabriel.products.controller;

import java.util.List;

import com.service.gabriel.products.dto.ApiResponse;
import com.service.gabriel.products.dto.CreateProductRequest;
import com.service.gabriel.products.dto.ProductResponse;
import com.service.gabriel.products.security.AuthenticatedUser;
import com.service.gabriel.products.service.JwtService;
import com.service.gabriel.products.service.ProductService;
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
@RequestMapping("/products")
public class ProductController {

	private final ProductService productService;
	private final JwtService jwtService;

	public ProductController(ProductService productService, JwtService jwtService) {
		this.productService = productService;
		this.jwtService = jwtService;
	}

	@GetMapping
	public ApiResponse<List<ProductResponse>> listAll() {
		return ApiResponse.ok("Produtos encontrados", productService.listAll());
	}

	@GetMapping("/{id}")
	public ApiResponse<ProductResponse> getById(@PathVariable String id) {
		return ApiResponse.ok("Produto encontrado", productService.getById(id));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<ProductResponse>> create(
			@Valid @RequestBody CreateProductRequest request,
			@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
		AuthenticatedUser authenticatedUser = jwtService.validateAuthorizationHeader(authorizationHeader);
		ProductResponse product = productService.create(request, authenticatedUser);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok("Produto criado com sucesso", product));
	}
}
