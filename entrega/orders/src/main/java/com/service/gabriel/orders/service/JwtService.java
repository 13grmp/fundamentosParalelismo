package com.service.gabriel.orders.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.service.gabriel.orders.model.Role;
import com.service.gabriel.orders.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class JwtService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final ObjectMapper objectMapper;
	private final String secret;

	public JwtService(ObjectMapper objectMapper, @Value("${jwt.secret}") String secret) {
		this.objectMapper = objectMapper;
		this.secret = secret;
	}

	public AuthenticatedUser validateAuthorizationHeader(String authorizationHeader) {
		if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT ausente");
		}
		return validateToken(authorizationHeader.substring("Bearer ".length()).trim());
	}

	@SuppressWarnings("unchecked")
	public AuthenticatedUser validateToken(String token) {
		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT invalido");
		}

		String unsignedToken = parts[0] + "." + parts[1];
		if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura JWT invalida");
		}

		try {
			String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
			Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
			Number exp = (Number) payload.get("exp");
			if (exp == null || exp.longValue() < Instant.now().getEpochSecond()) {
				throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT expirado");
			}

			return new AuthenticatedUser(
					String.valueOf(payload.get("userId")),
					String.valueOf(payload.get("email")),
					Role.fromTokenValue(String.valueOf(payload.get("role"))));
		} catch (ResponseStatusException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT invalido", ex);
		}
	}

	private String sign(String value) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("Nao foi possivel validar assinatura JWT", ex);
		}
	}

	private boolean constantTimeEquals(String expected, String actual) {
		return java.security.MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8),
				actual.getBytes(StandardCharsets.UTF_8));
	}
}
