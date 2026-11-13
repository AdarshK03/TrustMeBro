package com.platform.gateway.cache;

import com.platform.gateway.config.GatewayTrafficProperties;
import com.platform.gateway.filter.ProductResponseCacheFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ProductResponseCacheFilterTest {

    private GatewayTrafficProperties properties;
    private GatewayCacheManager cacheManager;
    private ProductResponseCacheFilter filter;

    @BeforeEach
    void setUp() {
        properties = new GatewayTrafficProperties();
        properties.setCacheEnabled(true);
        properties.setCacheExpirationSeconds(60);
        properties.setCacheMaxSize(100);

        cacheManager = new GatewayCacheManager(properties);
        filter = new ProductResponseCacheFilter(properties, cacheManager);
    }

    @Test
    void cacheDisabled_AlwaysReachesDownstream() {
        properties.setCacheEnabled(false);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        filter.filter(exchange, ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        }).block();

        assertTrue(downstreamCalled.get());
        assertNull(exchange.getResponse().getHeaders().getFirst(ProductResponseCacheFilter.X_CACHE_HEADER));
    }

    @Test
    void cacheMiss_ThenHit_ServesFromCacheWithoutCallingDownstream() {
        // Pre-populate cache for /api/products/1
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        cacheManager.put("/api/products/1", new CachedResponse(
                HttpStatus.OK,
                headers,
                "{\"id\":1,\"name\":\"Keyboard\"}".getBytes(StandardCharsets.UTF_8)
        ));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/1").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        filter.filter(exchange, ex -> {
            downstreamCalled.set(true);
            return Mono.empty();
        }).block();

        // Downstream should NOT be called because it was a cache hit!
        assertFalse(downstreamCalled.get(), "Downstream must NOT be called on cache hit");
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        assertEquals(ProductResponseCacheFilter.CACHE_HIT, exchange.getResponse().getHeaders().getFirst(ProductResponseCacheFilter.X_CACHE_HEADER));
    }

    @Test
    void cacheMiss_PopulatesCacheOnSuccessfulResponse() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/2").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain mockDownstream = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = ex.getResponse().bufferFactory().wrap("{\"id\":2,\"name\":\"Mouse\"}".getBytes(StandardCharsets.UTF_8));
            return ex.getResponse().writeWith(Mono.just(buffer));
        };

        filter.filter(exchange, mockDownstream).block();

        // Response should be tagged MISS
        assertEquals(ProductResponseCacheFilter.CACHE_MISS, exchange.getResponse().getHeaders().getFirst(ProductResponseCacheFilter.X_CACHE_HEADER));

        // Now cache should contain entry for /api/products/2
        CachedResponse cached = cacheManager.get("/api/products/2");
        assertNotNull(cached, "Cache must store successful downstream response");
        assertEquals("{\"id\":2,\"name\":\"Mouse\"}", new String(cached.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void productUpdate_InvalidatesCache() {
        // Pre-populate product 3 and listing
        cacheManager.put("/api/products/3", new CachedResponse(HttpStatus.OK, new HttpHeaders(), "item3".getBytes()));
        cacheManager.put("/api/products", new CachedResponse(HttpStatus.OK, new HttpHeaders(), "list".getBytes()));

        MockServerHttpRequest request = MockServerHttpRequest.put("/api/products/3").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain mockDownstream = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };

        filter.filter(exchange, mockDownstream).block();

        // Product 3 and listing must now be invalidated
        assertNull(cacheManager.get("/api/products/3"));
        assertNull(cacheManager.get("/api/products"));
    }

    @Test
    void failedDownstreamResponse_IsNotCached() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/999").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain mockErrorDownstream = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            DataBuffer buffer = ex.getResponse().bufferFactory().wrap("{\"error\":\"Server Error\"}".getBytes(StandardCharsets.UTF_8));
            return ex.getResponse().writeWith(Mono.just(buffer));
        };

        filter.filter(exchange, mockErrorDownstream).block();

        // Must NOT be stored in cache
        assertNull(cacheManager.get("/api/products/999"), "500 Internal Server Error must not be cached");
    }
}

