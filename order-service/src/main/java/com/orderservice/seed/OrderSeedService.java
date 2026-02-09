package com.orderservice.seed;

import com.orderservice.client.ProductPageResponse;
import com.orderservice.client.ProductResponse;
import com.orderservice.client.ProductServiceClient;
import com.orderservice.entity.Order;
import com.orderservice.entity.OrderItem;
import com.orderservice.entity.OrderStatus;
import com.orderservice.entity.SeedRun;
import com.orderservice.repository.OrderRepository;
import com.orderservice.repository.SeedRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSeedService {

    private final OrderRepository orderRepository;
    private final SeedRunRepository seedRunRepository;
    private final MongoTemplate mongoTemplate;
    private final ProductServiceClient productServiceClient;
    private final WebClient productServiceWebClient;

    @Value("${app.seed.reserve-inventory:false}")
    private boolean defaultReserveInventory;

    private static final String DATA_TAG = "seed-v1";
    private static final int CHUNK_SIZE = 200;
    private static final int PRODUCT_PAGE_SIZE = 100;
    private static final int CUSTOMER_COUNT = 200;
    private static final int CUSTOMER_START = 1001;

    public SeedRun seed(int count, long seedValue, boolean reset, boolean reserveInventory) {
        Instant startedAt = Instant.now();
        long startTime = System.currentTimeMillis();
        log.info("Starting order seed: count={}, seed={}, reset={}, reserveInventory={}",
                count, seedValue, reset, reserveInventory);

        try {
            if (reset) {
                clearSeededData();
            }

            long existingCount = orderRepository.countByDataTag(DATA_TAG);
            if (!reset && existingCount > 0) {
                log.info("Seeded data already exists ({} orders) and reset=false, skipping", existingCount);
                return saveSeedRun(startedAt, seedValue, count, 0,
                        System.currentTimeMillis() - startTime, "SKIPPED",
                        "Seeded data already exists (" + existingCount + " orders). Use reset=true to clear and reseed.",
                        reserveInventory, 0);
            }

            List<ProductResponse> products = fetchActiveProducts();
            if (products.isEmpty()) {
                return saveSeedRun(startedAt, seedValue, count, 0,
                        System.currentTimeMillis() - startTime, "FAILED",
                        "No active products found in product-service. Seed product-service first.",
                        reserveInventory, 0);
            }
            log.info("Fetched {} active products from product-service", products.size());

            Random random = new Random(seedValue);
            List<Order> orders = generateOrders(count, products, random, reserveInventory);

            insertOrdersInChunks(orders);

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("Order seed completed in {}ms: {} orders inserted", durationMs, orders.size());

            return saveSeedRun(startedAt, seedValue, count, durationMs, durationMs, "SUCCESS",
                    String.format("Seeded %d orders using %d products", orders.size(), products.size()),
                    reserveInventory, products.size());

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Order seed failed after {}ms: {}", durationMs, e.getMessage(), e);
            return saveSeedRun(startedAt, seedValue, count, durationMs, durationMs, "FAILED",
                    e.getMessage(), reserveInventory, 0);
        }
    }

    List<ProductResponse> fetchActiveProducts() {
        List<ProductResponse> allProducts = new ArrayList<>();
        int page = 0;
        boolean hasMore = true;

        while (hasMore) {
            final int currentPage = page;
            try {
                ProductPageResponse pageResponse = productServiceWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v1/products")
                                .queryParam("page", currentPage)
                                .queryParam("size", PRODUCT_PAGE_SIZE)
                                .queryParam("active", true)
                                .build())
                        .retrieve()
                        .bodyToMono(ProductPageResponse.class)
                        .block();

                if (pageResponse == null || pageResponse.getContent() == null || pageResponse.getContent().isEmpty()) {
                    hasMore = false;
                } else {
                    for (ProductResponse p : pageResponse.getContent()) {
                        if (p.isActive()) {
                            allProducts.add(p);
                        }
                    }
                    hasMore = !pageResponse.isLast();
                }
            } catch (Exception e) {
                log.warn("Error fetching products page {}: {}", currentPage, e.getMessage());
                hasMore = false;
            }
            page++;

            if (allProducts.size() >= 1000) {
                break;
            }
        }

        return allProducts;
    }

    List<Order> generateOrders(int count, List<ProductResponse> products, Random random, boolean reserveInventory) {
        List<Order> orders = new ArrayList<>(count);
        Instant now = Instant.now();
        long sixMonthsInSeconds = 180L * 24 * 60 * 60;

        for (int i = 0; i < count; i++) {
            String customerId = "CUST-" + (CUSTOMER_START + random.nextInt(CUSTOMER_COUNT));
            int itemCount = 1 + random.nextInt(5);
            List<OrderItem> items = new ArrayList<>(itemCount);

            for (int j = 0; j < itemCount; j++) {
                ProductResponse product = products.get(random.nextInt(products.size()));
                int quantity = 1 + random.nextInt(4);
                BigDecimal unitPrice = product.getPrice();
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

                items.add(OrderItem.builder()
                        .productId(product.getProductId())
                        .sku(product.getSku())
                        .productName(product.getName())
                        .quantity(quantity)
                        .unitPrice(unitPrice)
                        .lineTotal(lineTotal)
                        .build());
            }

            BigDecimal orderTotal = items.stream()
                    .map(OrderItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            OrderStatus status = pickStatus(random);
            Instant createdAt = now.minus((long) (random.nextDouble() * sixMonthsInSeconds), ChronoUnit.SECONDS);

            if (reserveInventory && status != OrderStatus.CANCELLED) {
                reserveItemsWithRetry(items, products, random);
            }

            Order order = Order.builder()
                    .orderId(UUID.randomUUID().toString())
                    .customerId(customerId)
                    .status(status)
                    .items(items)
                    .orderTotal(orderTotal)
                    .currency("USD")
                    .createdAt(createdAt)
                    .updatedAt(createdAt)
                    .dataTag(DATA_TAG)
                    .build();

            orders.add(order);

            if ((i + 1) % 500 == 0) {
                log.info("Generated {}/{} orders", i + 1, count);
            }
        }

        return orders;
    }

    private OrderStatus pickStatus(Random random) {
        double roll = random.nextDouble();
        if (roll < 0.70) {
            return OrderStatus.CONFIRMED;
        } else if (roll < 0.90) {
            return OrderStatus.CREATED;
        } else {
            return OrderStatus.CANCELLED;
        }
    }

    private void reserveItemsWithRetry(List<OrderItem> items, List<ProductResponse> products, Random random) {
        for (int idx = 0; idx < items.size(); idx++) {
            OrderItem item = items.get(idx);
            int retries = 0;
            boolean reserved = false;

            while (!reserved && retries < 3) {
                try {
                    productServiceClient.reserveInventory(item.getProductId(), item.getQuantity());
                    reserved = true;
                } catch (Exception e) {
                    retries++;
                    log.debug("Reserve failed for product {} (attempt {}): {}", item.getProductId(), retries, e.getMessage());
                    if (retries < 3) {
                        ProductResponse alt = products.get(random.nextInt(products.size()));
                        BigDecimal lineTotal = alt.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP);
                        items.set(idx, OrderItem.builder()
                                .productId(alt.getProductId())
                                .sku(alt.getSku())
                                .productName(alt.getName())
                                .quantity(item.getQuantity())
                                .unitPrice(alt.getPrice())
                                .lineTotal(lineTotal)
                                .build());
                        item = items.get(idx);
                    }
                }
            }
        }
    }

    private void insertOrdersInChunks(List<Order> orders) {
        log.info("Inserting {} orders in chunks of {}...", orders.size(), CHUNK_SIZE);

        for (int i = 0; i < orders.size(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, orders.size());
            List<Order> chunk = orders.subList(i, end);
            orderRepository.saveAll(chunk);

            if ((i / CHUNK_SIZE + 1) % 5 == 0 || end == orders.size()) {
                log.info("Inserted orders: {}/{}", end, orders.size());
            }
        }
    }

    private void clearSeededData() {
        log.info("Clearing seeded order data (dataTag={})...", DATA_TAG);
        Query query = new Query(Criteria.where("dataTag").is(DATA_TAG));
        long deleted = mongoTemplate.remove(query, Order.class).getDeletedCount();
        log.info("Deleted {} seeded orders", deleted);
    }

    private SeedRun saveSeedRun(Instant startedAt, long seedValue, int count, long durationMs,
                                long actualDurationMs, String status, String message,
                                boolean reserveInventory, int productsFetched) {
        SeedRun seedRun = SeedRun.builder()
                .startedAt(startedAt)
                .finishedAt(Instant.now())
                .seed(seedValue)
                .count(count)
                .durationMs(actualDurationMs)
                .status(status)
                .message(message)
                .reserveInventory(reserveInventory)
                .productsFetched(productsFetched)
                .build();
        return seedRunRepository.save(seedRun);
    }

    public SeedStatus getSeedStatus() {
        long totalOrders = orderRepository.count();
        long seededOrders = orderRepository.countByDataTag(DATA_TAG);
        Optional<SeedRun> latestRun = seedRunRepository.findTopByOrderByStartedAtDesc();
        return new SeedStatus(totalOrders, seededOrders, latestRun.orElse(null));
    }

    public record SeedStatus(long totalOrders, long seededOrders, SeedRun lastSeedRun) {}
}
