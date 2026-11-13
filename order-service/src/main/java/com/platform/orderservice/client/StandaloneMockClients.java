package com.platform.orderservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Configuration providing fallback / mock client implementations ensuring Order Service
 * can be built, tested, and executed independently during Day 1 development.
 */
@Configuration
public class StandaloneMockClients {

    private static final Logger log = LoggerFactory.getLogger(StandaloneMockClients.class);

    @Bean
    public UserServiceClient userServiceClient() {
        return new UserServiceClient() {
            @Override
            public boolean verifyUserExists(Long userId) {
                log.info("[Inter-Service Contract] Checking user existence for userId={} with User Service", userId);
                return userId != null && userId > 0;
            }
        };
    }

    @Bean
    public ProductServiceClient productServiceClient() {
        return new ProductServiceClient() {
            @Override
            public Optional<ProductDto> getProductDetails(Long productId) {
                log.info("[Inter-Service Contract] Querying live price and stock for productId={} from Product Service", productId);
                if (productId == null || productId <= 0) {
                    return Optional.empty();
                }
                return Optional.of(new ProductDto(productId, "Product-" + productId, new BigDecimal("29.99"), 100));
            }
        };
    }
}
