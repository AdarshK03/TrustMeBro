package com.platform.gateway.controller;

import com.platform.gateway.cache.GatewayCacheManager;
import com.platform.gateway.config.GatewayTrafficProperties;
import com.platform.gateway.ratelimit.InMemoryRateLimiter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes live diagnostic metrics and telemetry for API Gateway optimizations:
 * Rate Limiting and Response Caching.
 */
@RestController
@RequestMapping("/api/gateway")
public class GatewayOptimizationController {

    private final GatewayTrafficProperties trafficProperties;
    private final InMemoryRateLimiter rateLimiter;
    private final GatewayCacheManager cacheManager;

    public GatewayOptimizationController(GatewayTrafficProperties trafficProperties,
                                         InMemoryRateLimiter rateLimiter,
                                         GatewayCacheManager cacheManager) {
        this.trafficProperties = trafficProperties;
        this.rateLimiter = rateLimiter;
        this.cacheManager = cacheManager;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getOptimizationStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // Rate Limiting Stats
        Map<String, Object> rlStats = new LinkedHashMap<>();
        rlStats.put("enabled", trafficProperties.isRateLimitEnabled());
        rlStats.put("maxRequests", trafficProperties.getRateLimitMaxRequests());
        rlStats.put("windowSeconds", trafficProperties.getRateLimitWindowSeconds());
        rlStats.put("trackedClients", rateLimiter.getTrackedClientsCount());
        stats.put("rateLimiting", rlStats);

        // Caching Stats
        Map<String, Object> cacheStats = new LinkedHashMap<>();
        long hits = cacheManager.getHitCount();
        long misses = cacheManager.getMissCount();
        long total = hits + misses;
        double hitRatio = total > 0 ? ((double) hits / total) * 100.0 : 0.0;

        cacheStats.put("enabled", trafficProperties.isCacheEnabled());
        cacheStats.put("hitCount", hits);
        cacheStats.put("missCount", misses);
        cacheStats.put("totalLookups", total);
        cacheStats.put("hitRatioPercent", Math.round(hitRatio * 100.0) / 100.0);
        cacheStats.put("invalidationCount", cacheManager.getInvalidationCount());
        cacheStats.put("estimatedSize", cacheManager.getEstimatedSize());
        cacheStats.put("expirationSeconds", trafficProperties.getCacheExpirationSeconds());
        cacheStats.put("maxSize", trafficProperties.getCacheMaxSize());
        stats.put("caching", cacheStats);

        return ResponseEntity.ok(stats);
    }

    @PostMapping("/cache/reset")
    public ResponseEntity<Map<String, String>> resetCache() {
        cacheManager.resetMetrics();
        return ResponseEntity.ok(Map.of("message", "Gateway response cache and metrics successfully reset."));
    }

    @PostMapping("/rate-limit/reset")
    public ResponseEntity<Map<String, String>> resetRateLimit() {
        rateLimiter.reset();
        return ResponseEntity.ok(Map.of("message", "Gateway rate limiter sliding windows successfully reset."));
    }
}

