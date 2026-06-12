package com.service.gabriel.products.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.service.gabriel.products.model.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonProductRepository {

	private final ObjectMapper objectMapper;
	private final Path primaryFile;
	private final Path replicaFile;
	private final JavaType productsListType;
	private final AtomicInteger readCounter = new AtomicInteger();

	public JsonProductRepository(
			ObjectMapper objectMapper,
			@Value("${storage.products.primary-file}") String primaryFile,
			@Value("${storage.products.replica-file}") String replicaFile) {
		this.objectMapper = objectMapper;
		this.primaryFile = Paths.get(primaryFile);
		this.replicaFile = Paths.get(replicaFile);
		this.productsListType = objectMapper.getTypeFactory().constructCollectionType(List.class, Product.class);
	}

	public synchronized List<Product> findAllRoundRobin() {
		return new ArrayList<>(readProducts(nextReadReplica()));
	}

	public synchronized Optional<Product> findByIdRoundRobin(String id) {
		return readProducts(nextReadReplica()).stream()
				.filter(product -> product.getId().equals(id))
				.findFirst();
	}

	public synchronized Optional<Product> findByIdPrimary(String id) {
		return readProducts(primaryFile).stream()
				.filter(product -> product.getId().equals(id))
				.findFirst();
	}

	public synchronized Product saveToAllReplicas(Product product) {
		List<Product> primaryProducts = upsert(readProducts(primaryFile), product);
		List<Product> replicaProducts = upsert(readProducts(replicaFile), product);

		writeProducts(primaryFile, primaryProducts);
		try {
			writeProducts(replicaFile, replicaProducts);
		} catch (RuntimeException ex) {
			throw new IllegalStateException("Falha ao gravar replica de produtos; escrita nao foi confirmada", ex);
		}

		return product;
	}

	public synchronized void deleteAll() {
		writeProducts(primaryFile, new ArrayList<>());
		writeProducts(replicaFile, new ArrayList<>());
		readCounter.set(0);
	}

	public Path primaryFile() {
		return primaryFile;
	}

	public Path replicaFile() {
		return replicaFile;
	}

	private List<Product> upsert(List<Product> products, Product product) {
		for (int index = 0; index < products.size(); index++) {
			if (products.get(index).getId().equals(product.getId())) {
				products.set(index, product);
				return products;
			}
		}

		products.add(product);
		return products;
	}

	private Path nextReadReplica() {
		return Math.floorMod(readCounter.getAndIncrement(), 2) == 0 ? primaryFile : replicaFile;
	}

	private List<Product> readProducts(Path file) {
		try {
			if (Files.notExists(file) || Files.size(file) == 0) {
				return new ArrayList<>();
			}
			return objectMapper.readValue(file, productsListType);
		} catch (Exception ex) {
			throw new IllegalStateException("Nao foi possivel ler produtos em " + file, ex);
		}
	}

	private void writeProducts(Path file, List<Product> products) {
		try {
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, products);
		} catch (Exception ex) {
			throw new IllegalStateException("Nao foi possivel gravar produtos em " + file, ex);
		}
	}
}
