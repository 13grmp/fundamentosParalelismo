package com.service.gabriel.users.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.service.gabriel.users.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonUserRepository {

	private final ObjectMapper objectMapper;
	private final Path storageFile;
	private final JavaType usersListType;

	public JsonUserRepository(ObjectMapper objectMapper, @Value("${storage.users.file}") String storageFile) {
		this.objectMapper = objectMapper;
		this.storageFile = Paths.get(storageFile);
		this.usersListType = objectMapper.getTypeFactory().constructCollectionType(List.class, User.class);
	}

	public synchronized List<User> findAll() {
		return new ArrayList<>(readUsers());
	}

	public synchronized Optional<User> findById(String id) {
		return readUsers().stream()
				.filter(user -> user.getId().equals(id))
				.findFirst();
	}

	public synchronized Optional<User> findByEmail(String email) {
		String normalizedEmail = normalizeEmail(email);
		return readUsers().stream()
				.filter(user -> user.getEmail().equals(normalizedEmail))
				.findFirst();
	}

	public synchronized boolean existsByEmail(String email) {
		return findByEmail(email).isPresent();
	}

	public synchronized User save(User user) {
		List<User> users = readUsers();
		boolean replaced = false;

		for (int index = 0; index < users.size(); index++) {
			if (users.get(index).getId().equals(user.getId())) {
				users.set(index, user);
				replaced = true;
				break;
			}
		}

		if (!replaced) {
			users.add(user);
		}

		writeUsers(users);
		return user;
	}

	public synchronized void deleteAll() {
		writeUsers(new ArrayList<>());
	}

	private List<User> readUsers() {
		try {
			if (Files.notExists(storageFile) || Files.size(storageFile) == 0) {
				return new ArrayList<>();
			}
			return objectMapper.readValue(storageFile, usersListType);
		} catch (Exception ex) {
			throw new IllegalStateException("Nao foi possivel ler usuarios em " + storageFile, ex);
		}
	}

	private void writeUsers(List<User> users) {
		try {
			Path parent = storageFile.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, users);
		} catch (Exception ex) {
			throw new IllegalStateException("Nao foi possivel gravar usuarios em " + storageFile, ex);
		}
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
