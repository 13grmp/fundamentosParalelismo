package com.service.gabriel.users.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.service.gabriel.users.model.Role;
import com.service.gabriel.users.model.User;
import com.service.gabriel.users.security.AuthenticatedUser;
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
	private final long expirationMinutes;

	public JwtService(
			ObjectMapper objectMapper,
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration-minutes:60}") long expirationMinutes) {
		this.objectMapper = objectMapper;
		this.secret = secret;
		this.expirationMinutes = expirationMinutes;
	}

	public String createToken(User user) {
		long expiresAt = Instant.now().plusSeconds(expirationMinutes * 60).getEpochSecond();

		Map<String, Object> header = new LinkedHashMap<>();
		header.put("alg", "HS256");
		header.put("typ", "JWT");

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("userId", user.getId());
		payload.put("email", user.getEmail());
		payload.put("role", user.getRole().tokenValue());
		payload.put("exp", expiresAt);

		String encodedHeader = encodeJson(header);
		String encodedPayload = encodeJson(payload);
		String unsignedToken = encodedHeader + "." + encodedPayload;
		return unsignedToken + "." + sign(unsignedToken);
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
		String expectedSignature = sign(unsignedToken);
		if (!constantTimeEquals(expectedSignature, parts[2])) {
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
		} catch (RuntimeException ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT invalido", ex);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT invalido", ex);
		}
	}

	private String encodeJson(Map<String, Object> values) {
		try {
			String json = objectMapper.writeValueAsString(values);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		} catch (Exception ex) {
			throw new IllegalStateException("Nao foi possivel gerar JWT", ex);
		}
	}

	private String sign(String value) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception ex) {
			throw new IllegalStateException("Nao foi possivel assinar JWT", ex);
		}
	}

	private boolean constantTimeEquals(String expected, String actual) {
		return MessageDigestHolder.equals(
				expected.getBytes(StandardCharsets.UTF_8),
				actual.getBytes(StandardCharsets.UTF_8));
	}

	private static final class MessageDigestHolder {

		private MessageDigestHolder() {
		}

		private static boolean equals(byte[] expected, byte[] actual) {
			return java.security.MessageDigest.isEqual(expected, actual);
		}
	}
}
