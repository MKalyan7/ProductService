package com.productservice.service;

import com.productservice.entity.Category;
import com.productservice.entity.Inventory;
import com.productservice.entity.Product;
import com.productservice.entity.SeedRun;
import com.productservice.repository.CategoryRepository;
import com.productservice.repository.InventoryRepository;
import com.productservice.repository.ProductRepository;
import com.productservice.repository.SeedRunRepository;
import com.productservice.seed.DataGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeedService {

    private final DataGenerator dataGenerator;
    private final MongoTemplate mongoTemplate;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SeedRunRepository seedRunRepository;

    private static final int CHUNK_SIZE = 300;

    public SeedRun seed(int productCount, long seed, boolean reset) {
        long startTime = System.currentTimeMillis();
        log.info("Starting seed operation: productCount={}, seed={}, reset={}", productCount, seed, reset);

        try {
            if (reset) {
                clearExistingData();
            }

            if (!reset && hasExistingData()) {
                log.info("Data already exists and reset=false, skipping seed");
                return createSeedRun(seed, productCount, 0, "SKIPPED", "Data already exists. Use reset=true to clear and reseed.");
            }

            DataGenerator.GeneratedData data = dataGenerator.generate(seed, productCount);

            insertCategories(data.getCategories());
            insertProducts(data.getProducts());
            insertInventories(data.getInventories());

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("Seed operation completed in {}ms: {} categories, {} products, {} inventories",
                    durationMs, data.getCategories().size(), data.getProducts().size(), data.getInventories().size());

            return createSeedRun(seed, productCount, durationMs, "SUCCESS",
                    String.format("Seeded %d categories, %d products, %d inventories",
                            data.getCategories().size(), data.getProducts().size(), data.getInventories().size()));

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Seed operation failed after {}ms: {}", durationMs, e.getMessage(), e);
            return createSeedRun(seed, productCount, durationMs, "FAILED", e.getMessage());
        }
    }

    private void clearExistingData() {
        log.info("Clearing existing data...");
        mongoTemplate.remove(new Query(), Product.class);
        mongoTemplate.remove(new Query(), Category.class);
        mongoTemplate.remove(new Query(), Inventory.class);
        log.info("Existing data cleared");
    }

    private boolean hasExistingData() {
        return categoryRepository.count() > 0 || productRepository.count() > 0;
    }

    private void insertCategories(List<Category> categories) {
        log.info("Inserting {} categories...", categories.size());

        for (int i = 0; i < categories.size(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, categories.size());
            List<Category> chunk = categories.subList(i, end);

            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Category.class);
            for (Category category : chunk) {
                Query query = new Query(Criteria.where("categoryId").is(category.getCategoryId()));
                bulkOps.upsert(query, new org.springframework.data.mongodb.core.query.Update()
                        .set("categoryId", category.getCategoryId())
                        .set("name", category.getName())
                        .set("description", category.getDescription())
                        .setOnInsert("createdAt", Instant.now())
                        .set("updatedAt", Instant.now()));
            }
            bulkOps.execute();
            log.debug("Inserted category chunk {}-{}", i, end);
        }
    }

    private void insertProducts(List<Product> products) {
        log.info("Inserting {} products in chunks of {}...", products.size(), CHUNK_SIZE);

        for (int i = 0; i < products.size(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, products.size());
            List<Product> chunk = products.subList(i, end);

            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Product.class);
            for (Product product : chunk) {
                Query query = new Query(Criteria.where("sku").is(product.getSku()));
                bulkOps.upsert(query, new org.springframework.data.mongodb.core.query.Update()
                        .set("productId", product.getProductId())
                        .set("sku", product.getSku())
                        .set("name", product.getName())
                        .set("description", product.getDescription())
                        .set("categoryId", product.getCategoryId())
                        .set("price", product.getPrice())
                        .set("currency", product.getCurrency())
                        .set("active", product.isActive())
                        .setOnInsert("createdAt", product.getCreatedAt())
                        .set("updatedAt", product.getUpdatedAt()));
            }
            bulkOps.execute();

            if ((i / CHUNK_SIZE + 1) % 5 == 0 || end == products.size()) {
                log.info("Inserted products: {}/{}", end, products.size());
            }
        }
    }

    private void insertInventories(List<Inventory> inventories) {
        log.info("Inserting {} inventory records in chunks of {}...", inventories.size(), CHUNK_SIZE);

        for (int i = 0; i < inventories.size(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, inventories.size());
            List<Inventory> chunk = inventories.subList(i, end);

            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Inventory.class);
            for (Inventory inventory : chunk) {
                Query query = new Query(Criteria.where("productId").is(inventory.getProductId()));
                bulkOps.upsert(query, new org.springframework.data.mongodb.core.query.Update()
                        .set("productId", inventory.getProductId())
                        .set("stockQty", inventory.getStockQty())
                        .set("reservedQty", inventory.getReservedQty())
                        .set("updatedAt", Instant.now()));
            }
            bulkOps.execute();

            if ((i / CHUNK_SIZE + 1) % 5 == 0 || end == inventories.size()) {
                log.info("Inserted inventories: {}/{}", end, inventories.size());
            }
        }
    }

    private SeedRun createSeedRun(long seed, int productCount, long durationMs, String status, String message) {
        SeedRun seedRun = SeedRun.builder()
                .lastSeedTime(Instant.now())
                .seed(seed)
                .productCount(productCount)
                .categoryCount(DataGenerator.getCategoryCount())
                .durationMs(durationMs)
                .status(status)
                .message(message)
                .build();
        return seedRunRepository.save(seedRun);
    }

    public Optional<SeedRun> getLatestSeedRun() {
        return seedRunRepository.findTopByOrderByLastSeedTimeDesc();
    }

    public SeedStatus getSeedStatus() {
        long categoryCount = categoryRepository.count();
        long productCount = productRepository.count();
        long inventoryCount = inventoryRepository.count();
        Optional<SeedRun> latestRun = getLatestSeedRun();

        return new SeedStatus(categoryCount, productCount, inventoryCount, latestRun.orElse(null));
    }

    public record SeedStatus(long categoryCount, long productCount, long inventoryCount, SeedRun lastSeedRun) {}
}
