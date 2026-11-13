package com.platform.gateway.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe sliding-window in-memory rate limiter.
 * 
 * NOTE: Rate limits are enforced locally per application instance in memory.
 * They are not shared across multiple distributed gateway instances.
 * Expired request records and inactive client keys are automatically pruned to prevent
 * unbounded memory growth.
 */
@Component
public class InMemoryRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRateLimiter.class);

    private final ConcurrentHashMap<String, Deque<Long>> clientWindows = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong(0);

    public RateLimitResult checkLimit(String clientKey, int maxRequests, long windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        long windowStart = now - windowMillis;

        // Periodically prune stale client entries every 500 requests
        if ((requestCounter.incrementAndGet() % 500) == 0) {
            pruneStaleClients(windowMillis);
        }

        Deque<Long> timestamps = clientWindows.computeIfAbsent(clientKey, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // Evict timestamps outside sliding window
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }

            int currentCount = timestamps.size();
            if (currentCount < maxRequests) {
                timestamps.addLast(now);
                int remaining = maxRequests - (currentCount + 1);
                return RateLimitResult.allow(maxRequests, remaining);
            } else {
                Long oldest = timestamps.peekFirst();
                long retryAfterMillis = (oldest != null) ? (oldest + windowMillis - now) : windowMillis;
                long retryAfterSec = Math.max(1, (retryAfterMillis + 999) / 1000);
                log.warn("Rate limit exceeded for clientKey={}: {}/{} requests in window {}s. Retry-After: {}s",
                        clientKey, currentCount, maxRequests, windowSeconds, retryAfterSec);
                return RateLimitResult.reject(maxRequests, retryAfterSec);
            }
        }
    }

    private void pruneStaleClients(long windowMillis) {
        long cutoff = System.currentTimeMillis() - windowMillis;
        Iterator<Map.Entry<String, Deque<Long>>> it = clientWindows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Deque<Long>> entry = it.next();
            Deque<Long> queue = entry.getValue();
            synchronized (queue) {
                while (!queue.isEmpty() && queue.peekFirst() <= cutoff) {
                    queue.pollFirst();
                }
                if (queue.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    public void reset() {
        clientWindows.clear();
        requestCounter.set(0);
        log.info("Rate limiter state reset.");
    }

    public int getTrackedClientsCount() {
        return clientWindows.size();
    }
}
