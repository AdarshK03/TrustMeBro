package com.platform.productservice.config;

import com.platform.productservice.model.Product;
import com.platform.productservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repeatable, idempotent seed data initializer for development and local testing.
 * Only executes when 'app.seed-data.enabled' or 'APP_SEED_DATA_ENABLED' is set to true.
 * Safely verifies non-existence before persisting to avoid creating duplicates or mutating existing data.
 */
@Component
public class ProductDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductDataInitializer.class);

    private final ProductRepository productRepository;

    @Value("${app.seed-data.enabled:false}")
    private boolean seedDataEnabled;

    public ProductDataInitializer(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (!seedDataEnabled) {
            log.info("Development seed data is disabled (app.seed-data.enabled=false). Skipping product initialization.");
            return;
        }

        log.info("Development seed data is enabled. Initializing default sample products if absent...");

        List<Product> sampleProducts = List.of(
                new Product("Ergonomic Mechanical Keyboard", "RGB backlit keyboard with custom mechanical switches", new BigDecimal("129.99"), 50),
                new Product("Ultra-Wide 4K Monitor", "34-inch curved IPS monitor with 144Hz refresh rate", new BigDecimal("499.99"), 20),
                new Product("Wireless Noise-Canceling Headphones", "Bluetooth 5.3 headphones with 40-hour battery life", new BigDecimal("199.99"), 75),
                new Product("Precision Wireless Mouse", "High-accuracy ergonomic laser mouse with adjustable DPI", new BigDecimal("59.99"), 120)
        );

        for (Product sample : sampleProducts) {
            if (productRepository.findByName(sample.getName()).isEmpty()) {
                productRepository.save(sample);
                log.info("Seeded product: {} - ${}", sample.getName(), sample.getPrice());
            } else {
                log.debug("Product already exists, skipping seed: {}", sample.getName());
            }
        }

        log.info("Product seed data verification complete. Total products: {}", productRepository.count());
    }
}
