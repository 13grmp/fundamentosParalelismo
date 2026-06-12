package com.service.gabriel.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@NotBlank(message = "Email e obrigatorio")
		@Email(message = "Email invalido")
		String email,
		@NotBlank(message = "Senha e obrigatoria")
		String password) {
}
