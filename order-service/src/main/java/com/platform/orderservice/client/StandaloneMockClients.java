package com.platform.orderservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Optional fallback mock client configuration that can be enabled via
 * configuration (services.mock.enabled=true) or standalone testing.
 */
@Configuration
@ConditionalOnProperty(name = "services.mock.enabled", havingValue = "true")
public class StandaloneMockClients {

    private static final Logger log = LoggerFactory.getLogger(StandaloneMockClients.class);

    @Bean
    public UserServiceClient mockUserServiceClient() {
        return new UserServiceClient() {
            @Override
            public boolean verifyUserExists(Long userId) {
                log.info("[Mock Inter-Service] Checking user existence for userId={}", userId);
                return userId != null && userId > 0;
            }
        };
    }

    @Bean
    public ProductServiceClient mockProductServiceClient() {
        return new ProductServiceClient() {
            @Override
            public Optional<ProductDto> getProductDetails(Long productId) {
                log.info("[Mock Inter-Service] Querying mock price and stock for productId={}", productId);
                if (productId == null || productId <= 0) {
                    return Optional.empty();
                }
                return Optional.of(new ProductDto(productId, "Product-" + productId, new BigDecimal("29.99"), 100));
            }
        };
    }
}
