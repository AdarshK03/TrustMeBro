package com.platform.gateway.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.platform.gateway.config.GatewayTrafficProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GatewayCacheManager {

    private static final Logger log = LoggerFactory.getLogger(GatewayCacheManager.class);

    private final GatewayTrafficProperties trafficProperties;
    private final Cache<String, CachedResponse> cache;

    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong invalidationCount = new AtomicLong(0);

    public GatewayCacheManager(GatewayTrafficProperties trafficProperties) {
        this.trafficProperties = trafficProperties;
        long expirationSeconds = Math.max(1, trafficProperties.getCacheExpirationSeconds());
        int maxSize = Math.max(10, trafficProperties.getCacheMaxSize());

        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofSeconds(expirationSeconds))
                .removalListener((key, value, cause) ->
                        log.info("[Gateway Cache] Entry evicted/expired: key={} cause={}", key, cause))
                .build();

        log.info("Initialized GatewayCacheManager with maxSize={} and expirationSeconds={}s",
                maxSize, expirationSeconds);
    }

    public CachedResponse get(String key) {
        CachedResponse cached = cache.getIfPresent(key);
        if (cached != null) {
            long hits = hitCount.incrementAndGet();
            log.info("[Gateway Cache] HIT for key: {} (total hits: {})", key, hits);
            return cached;
        } else {
            long misses = missCount.incrementAndGet();
            log.info("[Gateway Cache] MISS for key: {} (total misses: {})", key, misses);
            return null;
        }
    }

    public void put(String key, CachedResponse response) {
        cache.put(key, response);
        log.info("[Gateway Cache] STORED response for key: {} (size: {} bytes, current cached entries: {})",
                key, response.getBody().length, cache.estimatedSize());
    }

    public void invalidate(String key) {
        if (cache.getIfPresent(key) != null) {
            cache.invalidate(key);
            invalidationCount.incrementAndGet();
            log.info("[Gateway Cache] INVALIDATED key: {}", key);
        }
    }

    public void invalidateProduct(Long productId) {
        long count = 0;
        if (productId != null) {
            String productKey = "/api/products/" + productId;
            if (cache.getIfPresent(productKey) != null) {
                cache.invalidate(productKey);
                count++;
            }
        }
        // Also invalidate the product listing cache
        if (cache.getIfPresent("/api/products") != null) {
            cache.invalidate("/api/products");
            count++;
        }
        invalidationCount.addAndGet(count);
        log.info("[Gateway Cache] INVALIDATED product id={} and /api/products listing cache (keys removed: {})",
                productId, count);
    }

    public void invalidateAll() {
        long count = cache.estimatedSize();
        cache.invalidateAll();
        invalidationCount.addAndGet(count);
        log.info("[Gateway Cache] INVALIDATED ALL entries (total removed: {})", count);
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public long getInvalidationCount() {
        return invalidationCount.get();
    }

    public long getEstimatedSize() {
        return cache.estimatedSize();
    }

    public void resetMetrics() {
        hitCount.set(0);
        missCount.set(0);
        invalidationCount.set(0);
        cache.invalidateAll();
        log.info("[Gateway Cache] Reset metrics and cleared cache.");
    }
}

