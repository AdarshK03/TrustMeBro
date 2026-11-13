package com.platform.orderservice.client;

import com.platform.orderservice.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Service
public class HttpProductServiceClient implements ProductServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpProductServiceClient.class);

    private final RestClient restClient;
    private final String productServiceUrl;

    public HttpProductServiceClient(RestClient.Builder restClientBuilder,
                                   @Value("${services.product.url:http://localhost:8082}") String productServiceUrl) {
        this.productServiceUrl = productServiceUrl.endsWith("/")
                ? productServiceUrl.substring(0, productServiceUrl.length() - 1)
                : productServiceUrl;
        this.restClient = restClientBuilder.baseUrl(this.productServiceUrl).build();
        log.info("Initialized HttpProductServiceClient targeting: {}", this.productServiceUrl);
    }

    @Override
    public Optional<ProductDto> getProductDetails(Long productId) {
        if (productId == null || productId <= 0) {
            return Optional.empty();
        }

        try {
            log.debug("Calling Product Service: GET {}/api/products/{}", productServiceUrl, productId);
            ProductDto product = restClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    .body(ProductDto.class);

            if (product == null || product.id() == null || product.price() == null) {
                log.debug("Product {} body was null or missing price", productId);
                return Optional.empty();
            }

            return Optional.of(product);
        } catch (HttpClientErrorException.NotFound ex) {
            log.debug("Product {} not found (404) in Product Service", productId);
            return Optional.empty();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("Client error querying product {}: {} - {}", productId, ex.getStatusCode(), ex.getMessage());
            throw new IllegalArgumentException("Product Service rejected request for product ID " + productId + ": " + ex.getStatusText());
        } catch (HttpServerErrorException ex) {
            log.error("Product Service returned 5xx server error for product {}: {}", productId, ex.getStatusCode());
            throw new ServiceUnavailableException("Product Service is temporarily unavailable. Please try again later.");
        } catch (ResourceAccessException ex) {
            log.error("Connection or timeout error reaching Product Service at {} for product {}: {}", productServiceUrl, productId, ex.getMessage());
            throw new ServiceUnavailableException("Product Service is currently unreachable. Please try again later.");
        } catch (Exception ex) {
            log.error("Unexpected error contacting Product Service for product {}: {}", productId, ex.getMessage());
            throw new ServiceUnavailableException("Unable to contact Product Service. Please try again later.");
        }
    }
}
