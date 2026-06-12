package com.service.gabriel.orders.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.service.gabriel.orders.model.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonOrderRepository {

	private final ObjectMapper objectMapper;
	private final Path storageFile;
	private final JavaType ordersListType;

	public JsonOrderRepository(ObjectMapper objectMapper, @Value("${storage.orders.file}") String storageFile) {
		this.objectMapper = objectMapper;
		this.storageFile = Paths.get(storageFile);
		this.ordersListType = objectMapper.getTypeFactory().constructCollectionType(List.class, Order.class);
	}

	public synchronized Order save(Order order) {
		List<Order> orders = readOrders();
		orders.add(order);
		writeOrders(orders);
		return order;
	}

	public synchronized List<Order> findByUserId(String userId) {
		return readOrders().stream()
				.filter(order -> order.getUserId().equals(userId))
				.toList();
	}

	public synchronized void deleteAll() {
		writeOrders(new ArrayList<>());
	}

	private List<Order> readOrders() {
		try {
			if (Files.notExists(storageFile) || Files.size(storageFile) == 0) {
				return new ArrayList<>();
			}
			return objectMapper.readValue(storageFile, ordersListType);
		} catch (Exception ex) {
			throw new IllegalStateException("Nao foi possivel ler pedidos em " + storageFile, ex);
		}
	}

	private void writeOrders(List<Order> orders) {
		try {
			Path parent = storageFile.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, orders);
		} catch (Exception ex) {
			throw new IllegalStateException("Nao foi possivel gravar pedidos em " + storageFile, ex);
		}
	}
}
