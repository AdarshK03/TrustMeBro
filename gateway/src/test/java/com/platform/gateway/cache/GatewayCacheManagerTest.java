package com.platform.gateway.cache;

import com.platform.gateway.config.GatewayTrafficProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GatewayCacheManagerTest {

    private GatewayTrafficProperties properties;
    private GatewayCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        properties = new GatewayTrafficProperties();
        properties.setCacheEnabled(true);
        properties.setCacheExpirationSeconds(60);
        properties.setCacheMaxSize(10);
        cacheManager = new GatewayCacheManager(properties);
    }

    @Test
    void cacheMiss_ThenStoreAndHit() {
        String key = "/api/products/1";
        assertNull(cacheManager.get(key));
        assertEquals(1, cacheManager.getMissCount());

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        CachedResponse response = new CachedResponse(HttpStatus.OK, headers, "{\"id\":1,\"name\":\"Laptop\"}".getBytes(StandardCharsets.UTF_8));

        cacheManager.put(key, response);

        CachedResponse retrieved = cacheManager.get(key);
        assertNotNull(retrieved);
        assertEquals(HttpStatus.OK, retrieved.getStatus());
        assertEquals("{\"id\":1,\"name\":\"Laptop\"}", new String(retrieved.getBody(), StandardCharsets.UTF_8));
        assertEquals(1, cacheManager.getHitCount());
    }

    @Test
    void invalidateProduct_RemovesDetailAndListing() {
        HttpHeaders headers = new HttpHeaders();
        cacheManager.put("/api/products/1", new CachedResponse(HttpStatus.OK, headers, "item1".getBytes()));
        cacheManager.put("/api/products/2", new CachedResponse(HttpStatus.OK, headers, "item2".getBytes()));
        cacheManager.put("/api/products", new CachedResponse(HttpStatus.OK, headers, "list".getBytes()));

        assertEquals(3, cacheManager.getEstimatedSize());

        cacheManager.invalidateProduct(1L);

        // Product 1 and listing must be gone
        assertNull(cacheManager.get("/api/products/1"));
        assertNull(cacheManager.get("/api/products"));
        // Product 2 remains
        assertNotNull(cacheManager.get("/api/products/2"));
        assertTrue(cacheManager.getInvalidationCount() >= 2);
    }

    @Test
    void cacheExpiration_EntryIsEvictedAfterTtl() throws InterruptedException {
        properties.setCacheExpirationSeconds(1); // 1 second TTL
        GatewayCacheManager shortLivedManager = new GatewayCacheManager(properties);

        shortLivedManager.put("/api/products/9", new CachedResponse(HttpStatus.OK, new HttpHeaders(), "item9".getBytes()));
        assertNotNull(shortLivedManager.get("/api/products/9"));

        // Wait 1.1s for entry to expire
        Thread.sleep(1100);

        assertNull(shortLivedManager.get("/api/products/9"));
    }

    @Test
    void resetMetrics_ClearsCacheAndCounters() {
        cacheManager.put("/api/products/1", new CachedResponse(HttpStatus.OK, new HttpHeaders(), "item1".getBytes()));
        cacheManager.get("/api/products/1");
        cacheManager.get("/api/products/missing");

        assertEquals(1, cacheManager.getHitCount());
        assertEquals(1, cacheManager.getMissCount());

        cacheManager.resetMetrics();

        assertEquals(0, cacheManager.getHitCount());
        assertEquals(0, cacheManager.getMissCount());
        assertEquals(0, cacheManager.getEstimatedSize());
    }
}

