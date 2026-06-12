package com.service.gabriel.users.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Service;

@Service
public class PasswordService {

	private static final int SALT_SIZE = 16;

	private final SecureRandom secureRandom = new SecureRandom();

	public String generateSalt() {
		byte[] salt = new byte[SALT_SIZE];
		secureRandom.nextBytes(salt);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
	}

	public String hash(String password, String salt) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 nao disponivel", ex);
		}
	}

	public boolean matches(String rawPassword, String salt, String expectedHash) {
		String actualHash = hash(rawPassword, salt);
		return MessageDigest.isEqual(
				actualHash.getBytes(StandardCharsets.UTF_8),
				expectedHash.getBytes(StandardCharsets.UTF_8));
	}
}
