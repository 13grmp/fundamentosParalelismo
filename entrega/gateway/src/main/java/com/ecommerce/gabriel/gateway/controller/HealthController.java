package com.ecommerce.gabriel.gateway.controller;

import com.ecommerce.gabriel.gateway.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	@GetMapping("/health")
	public HealthResponse health() {
		return new HealthResponse("ok");
	}
}
