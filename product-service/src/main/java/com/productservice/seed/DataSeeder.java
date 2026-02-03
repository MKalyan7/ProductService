package com.productservice.seed;

import com.productservice.entity.Category;
import com.productservice.entity.Inventory;
import com.productservice.entity.Product;
import com.productservice.repository.CategoryRepository;
import com.productservice.repository.InventoryRepository;
import com.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.seed", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    private final Random random = new Random();

    @Override
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            log.info("Data already exists, skipping seed");
            return;
        }

        log.info("Starting data seeding...");

        List<Category> categories = seedCategories();
        seedProducts(categories);

        log.info("Data seeding completed successfully");
    }

    private List<Category> seedCategories() {
        List<Category> categories = new ArrayList<>();

        String[][] categoryData = {
                {"Electronics", "Electronic devices and gadgets"},
                {"Clothing", "Apparel and fashion items"},
                {"Home & Garden", "Home improvement and garden supplies"},
                {"Sports & Outdoors", "Sports equipment and outdoor gear"},
                {"Books & Media", "Books, music, and digital media"}
        };

        for (String[] data : categoryData) {
            Category category = Category.builder()
                    .categoryId(UUID.randomUUID().toString())
                    .name(data[0])
                    .description(data[1])
                    .build();
            categories.add(categoryRepository.save(category));
            log.info("Created category: {}", data[0]);
        }

        return categories;
    }

    private void seedProducts(List<Category> categories) {
        String[][] productData = {
                {"Wireless Bluetooth Headphones", "High-quality wireless headphones with noise cancellation", "99.99"},
                {"Smart Watch Pro", "Advanced smartwatch with health monitoring features", "249.99"},
                {"Portable Power Bank", "20000mAh portable charger with fast charging", "39.99"},
                {"USB-C Hub Adapter", "7-in-1 USB-C hub with HDMI and card reader", "49.99"},
                {"Wireless Mouse", "Ergonomic wireless mouse with precision tracking", "29.99"},
                {"Cotton T-Shirt", "Premium cotton t-shirt, comfortable fit", "24.99"},
                {"Denim Jeans", "Classic fit denim jeans, durable material", "59.99"},
                {"Running Shoes", "Lightweight running shoes with cushioned sole", "89.99"},
                {"Winter Jacket", "Insulated winter jacket, water-resistant", "129.99"},
                {"Casual Sneakers", "Stylish casual sneakers for everyday wear", "69.99"},
                {"Garden Tool Set", "5-piece stainless steel garden tool set", "34.99"},
                {"LED Desk Lamp", "Adjustable LED desk lamp with USB charging port", "44.99"},
                {"Indoor Plant Pot", "Ceramic indoor plant pot with drainage", "19.99"},
                {"Kitchen Knife Set", "Professional 6-piece kitchen knife set", "79.99"},
                {"Throw Blanket", "Soft fleece throw blanket, machine washable", "29.99"},
                {"Yoga Mat", "Non-slip yoga mat with carrying strap", "24.99"},
                {"Camping Tent", "2-person waterproof camping tent", "149.99"},
                {"Basketball", "Official size basketball, indoor/outdoor", "34.99"},
                {"Hiking Backpack", "40L hiking backpack with rain cover", "69.99"},
                {"Fitness Tracker", "Water-resistant fitness tracker with heart rate monitor", "59.99"},
                {"Programming Guide", "Complete guide to modern programming", "49.99"},
                {"Cookbook Collection", "Best recipes from around the world", "34.99"},
                {"Science Fiction Novel", "Award-winning science fiction adventure", "14.99"},
                {"Music Album Vinyl", "Classic rock album on vinyl record", "29.99"},
                {"Documentary DVD Set", "Nature documentary collection, 5 discs", "39.99"}
        };

        int productIndex = 0;
        for (int i = 0; i < 25; i++) {
            Category category = categories.get(i / 5);
            String[] data = productData[productIndex++];

            String productId = UUID.randomUUID().toString();
            String sku = "SKU-" + String.format("%05d", i + 1);

            Product product = Product.builder()
                    .productId(productId)
                    .sku(sku)
                    .name(data[0])
                    .description(data[1])
                    .categoryId(category.getCategoryId())
                    .price(new BigDecimal(data[2]))
                    .currency("USD")
                    .active(true)
                    .build();

            productRepository.save(product);

            int stockQty = 10 + random.nextInt(191);
            int reservedQty = random.nextInt(Math.min(stockQty / 4, 20));

            Inventory inventory = Inventory.builder()
                    .productId(productId)
                    .stockQty(stockQty)
                    .reservedQty(reservedQty)
                    .build();

            inventoryRepository.save(inventory);

            log.info("Created product: {} with stock: {}", data[0], stockQty);
        }
    }
}
