package com.platform.orderservice.client;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Interface contract defining inter-service communication with Product Service.
 * Used during order checkout to verify stock and fetch current product pricing.
 */
public interface ProductServiceClient {
    
    record ProductDto(Long id, String name, BigDecimal price, Integer stock) {}

    Optional<ProductDto> getProductDetails(Long productId);
}
