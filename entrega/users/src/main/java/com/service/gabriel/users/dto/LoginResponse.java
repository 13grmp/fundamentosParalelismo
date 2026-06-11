package com.service.gabriel.users.dto;

public record LoginResponse(String token, UserResponse user) {
}
