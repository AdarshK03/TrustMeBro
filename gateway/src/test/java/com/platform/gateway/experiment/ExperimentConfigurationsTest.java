package com.platform.gateway.experiment;

import com.platform.gateway.cache.CachedResponse;
import com.platform.gateway.cache.GatewayCacheManager;
import com.platform.gateway.config.GatewayTrafficProperties;
import com.platform.gateway.filter.ProductResponseCacheFilter;
import com.platform.gateway.filter.RateLimitingFilter;
import com.platform.gateway.ratelimit.InMemoryRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentConfigurationsTest {

    private GatewayTrafficProperties properties;
    private InMemoryRateLimiter rateLimiter;
    private GatewayCacheManager cacheManager;
    private RateLimitingFilter rateLimitingFilter;
    private ProductResponseCacheFilter cachingFilter;

    @BeforeEach
    void setUp() {
        properties = new GatewayTrafficProperties();
        properties.setRateLimitMaxRequests(3);
        properties.setRateLimitWindowSeconds(60);
        properties.setCacheExpirationSeconds(60);
        properties.setCacheMaxSize(100);

        rateLimiter = new InMemoryRateLimiter();
        cacheManager = new GatewayCacheManager(properties);

        rateLimitingFilter = new RateLimitingFilter(properties, rateLimiter);
        cachingFilter = new ProductResponseCacheFilter(properties, cacheManager);
    }

    private Mono<Void> executeFilterChain(MockServerWebExchange exchange, AtomicInteger downstreamCallCount) {
        GatewayFilterChain terminalChain = ex -> {
            downstreamCallCount.incrementAndGet();
            ex.getResponse().setStatusCode(HttpStatus.OK);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = ex.getResponse().bufferFactory().wrap("{\"id\":10,\"name\":\"Gadget\"}".getBytes(StandardCharsets.UTF_8));
            return ex.getResponse().writeWith(Mono.just(buffer));
        };

        // Execution order: RateLimitingFilter -> CachingFilter -> Terminal Downstream
        return rateLimitingFilter.filter(exchange, ex -> cachingFilter.filter(ex, terminalChain));
    }

    @Test
    @DisplayName("Configuration A — Baseline: Rate Limiting Disabled, Caching Disabled")
    void testConfigurationA_Baseline() {
        properties.setRateLimitEnabled(false);
        properties.setCacheEnabled(false);

        AtomicInteger downstreamCalls = new AtomicInteger(0);

        // Send 10 consecutive requests for the same product
        for (int i = 0; i < 10; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.get("/api/products/10")
                    .header("X-Forwarded-For", "192.168.10.1")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(req);
            executeFilterChain(exchange, downstreamCalls).block();

            assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
            assertNull(exchange.getResponse().getHeaders().getFirst(ProductResponseCacheFilter.X_CACHE_HEADER));
        }

        // Every single request reached downstream (no caching, no rate limit blocking)
        assertEquals(10, downstreamCalls.get());
    }

    @Test
    @DisplayName("Configuration B — Rate Limit Only: Rate Limiting Enabled, Caching Disabled")
    void testConfigurationB_RateLimitOnly() {
        properties.setRateLimitEnabled(true);
        properties.setCacheEnabled(false);

        AtomicInteger downstreamCalls = new AtomicInteger(0);
        String clientIp = "192.168.10.2";

        // Requests 1, 2, 3 succeed up to limit
        for (int i = 0; i < 3; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.get("/api/products/10")
                    .header("X-Forwarded-For", clientIp)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(req);
            executeFilterChain(exchange, downstreamCalls).block();

            assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
            assertNull(exchange.getResponse().getHeaders().getFirst(ProductResponseCacheFilter.X_CACHE_HEADER));
        }
        assertEquals(3, downstreamCalls.get());

        // Request 4 exceeds limit and returns 429
        MockServerHttpRequest req4 = MockServerHttpRequest.get("/api/products/10")
                .header("X-Forwarded-For", clientIp)
                .build();
        MockServerWebExchange ex4 = MockServerWebExchange.from(req4);
        executeFilterChain(ex4, downstreamCalls).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex4.getResponse().getStatusCode());
        assertEquals(3, downstreamCalls.get(), "Downstream must NOT be called for 429 rejected request");
    }

    @Test
    @DisplayName("Configuration C — Cache Only: Rate Limiting Disabled, Caching Enabled")
    void testConfigurationC_CacheOnly() {
        properties.setRateLimitEnabled(false);
        properties.setCacheEnabled(true);

        AtomicInteger downstreamCalls = new AtomicInteger(0);

        // Request 1: Cache Miss -> calls downstream
        MockServerHttpRequest req1 = MockServerHttpRequest.get("/api/products/10")
                .header("X-Forwarded-For", "192.168.10.3")
                .build();
        MockServerWebExchange ex1 = MockServerWebExchange.from(req1);
        executeFilterChain(ex1, downstreamCalls).block();

        assertEquals(HttpStatus.OK, ex1.getResponse().getStatusCode());
        assertEquals(ProductResponseCacheFilter.CACHE_MISS, ex1.getResponse().getHeaders().getFirst(ProductResponseCacheFilter.X_CACHE_HEADER));
        assertEquals(1, downstreamCalls.get());

        // Requests 2 through 10: Cache Hits -> served directly, downstream calls remain 1!
        for (int i = 2; i <= 10; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.get("/api/products/10")
                    .header("X-Forwarded-For", "192.168.10.3")
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            executeFilterChain(ex, downstreamCalls).block();

            assertEquals(HttpStatus.OK, ex.getResponse().getStatusCode());
            assertEquals(ProductResponseCacheFilter.CACHE_HIT, ex.getResponse().getHeaders().getFirst(ProductResponseCacheFilter.X_CACHE_HEADER));
        }

        // Even after 10 requests, downstream was only invoked ONCE!
        assertEquals(1, downstreamCalls.get());
        assertEquals(9, cacheManager.getHitCount());
    }

    @Test
    @DisplayName("Configuration D — Combined: Rate Limiting Enabled, Caching Enabled")
    void testConfigurationD_Combined() {
        properties.setRateLimitEnabled(true);
        properties.setCacheEnabled(true);

        AtomicInteger downstreamCalls = new AtomicInteger(0);
        String clientIp = "192.168.10.4";

        // Request 1: Allowed by rate limiter, Cache MISS -> Downstream invoked once
        MockServerHttpRequest req1 = MockServerHttpRequest.get("/api/products/10")
                .header("X-Forwarded-For", clientIp)
                .build();
        MockServerWebExchange ex1 = MockServerWebExchange.from(req1);
        executeFilterChain(ex1, downstreamCalls).block();
        assertEquals(HttpStatus.OK, ex1.getResponse().getStatusCode());
        assertEquals(ProductResponseCacheFilter.CACHE_MISS, ex1.getResponse().getHeaders().getFirst(ProductResponseCacheFilter.X_CACHE_HEADER));
        assertEquals(1, downstreamCalls.get());

        // Request 2 & 3: Allowed by rate limiter, Cache HIT -> Served from cache
        for (int i = 2; i <= 3; i++) {
            MockServerHttpRequest req = MockServerHttpRequest.get("/api/products/10")
                    .header("X-Forwarded-For", clientIp)
                    .build();
            MockServerWebExchange ex = MockServerWebExchange.from(req);
            executeFilterChain(ex, downstreamCalls).block();
            assertEquals(HttpStatus.OK, ex.getResponse().getStatusCode());
            assertEquals(ProductResponseCacheFilter.CACHE_HIT, ex.getResponse().getHeaders().getFirst(ProductResponseCacheFilter.X_CACHE_HEADER));
        }
        assertEquals(1, downstreamCalls.get());

        // Request 4: Rate limit exceeded (limit=3) -> Rejected at rate limiter with 429
        MockServerHttpRequest req4 = MockServerHttpRequest.get("/api/products/10")
                .header("X-Forwarded-For", clientIp)
                .build();
        MockServerWebExchange ex4 = MockServerWebExchange.from(req4);
        executeFilterChain(ex4, downstreamCalls).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex4.getResponse().getStatusCode());
        assertEquals(1, downstreamCalls.get());
    }
}

