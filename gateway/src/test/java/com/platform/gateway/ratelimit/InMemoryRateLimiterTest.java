package com.platform.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRateLimiterTest {

    private InMemoryRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new InMemoryRateLimiter();
    }

    @Test
    void requestsUnderLimit_AreAllowed() {
        String client = "192.168.1.10";
        int limit = 5;
        long windowSec = 60;

        for (int i = 0; i < limit - 1; i++) {
            RateLimitResult result = rateLimiter.checkLimit(client, limit, windowSec);
            assertTrue(result.isAllowed());
            assertEquals(limit - (i + 1), result.getRemaining());
        }
    }

    @Test
    void requestAtLimit_IsAllowedAndRemainingIsZero() {
        String client = "192.168.1.20";
        int limit = 3;
        long windowSec = 60;

        rateLimiter.checkLimit(client, limit, windowSec);
        rateLimiter.checkLimit(client, limit, windowSec);
        RateLimitResult third = rateLimiter.checkLimit(client, limit, windowSec);

        assertTrue(third.isAllowed());
        assertEquals(0, third.getRemaining());
    }

    @Test
    void requestExceedingLimit_IsRejected() {
        String client = "192.168.1.30";
        int limit = 2;
        long windowSec = 60;

        assertTrue(rateLimiter.checkLimit(client, limit, windowSec).isAllowed());
        assertTrue(rateLimiter.checkLimit(client, limit, windowSec).isAllowed());

        RateLimitResult third = rateLimiter.checkLimit(client, limit, windowSec);
        assertFalse(third.isAllowed());
        assertEquals(0, third.getRemaining());
        assertTrue(third.getRetryAfterSeconds() > 0);
    }

    @Test
    void independentClients_HaveIndependentQuotas() {
        String clientA = "10.0.0.1";
        String clientB = "10.0.0.2";
        int limit = 2;
        long windowSec = 60;

        // Exhaust client A
        assertTrue(rateLimiter.checkLimit(clientA, limit, windowSec).isAllowed());
        assertTrue(rateLimiter.checkLimit(clientA, limit, windowSec).isAllowed());
        assertFalse(rateLimiter.checkLimit(clientA, limit, windowSec).isAllowed());

        // Client B is still fresh
        RateLimitResult bResult = rateLimiter.checkLimit(clientB, limit, windowSec);
        assertTrue(bResult.isAllowed());
        assertEquals(1, bResult.getRemaining());
    }

    @Test
    void windowReset_AllowsNewRequestsAfterExpiration() throws InterruptedException {
        String client = "10.0.0.99";
        int limit = 1;
        long windowSec = 1; // 1 second window

        assertTrue(rateLimiter.checkLimit(client, limit, windowSec).isAllowed());
        assertFalse(rateLimiter.checkLimit(client, limit, windowSec).isAllowed());

        // Wait 1.1s for window to roll over
        Thread.sleep(1100);

        assertTrue(rateLimiter.checkLimit(client, limit, windowSec).isAllowed());
    }

    @Test
    void reset_ClearsAllTracking() {
        String client = "192.168.1.50";
        rateLimiter.checkLimit(client, 1, 60);
        assertFalse(rateLimiter.checkLimit(client, 1, 60).isAllowed());

        rateLimiter.reset();
        assertEquals(0, rateLimiter.getTrackedClientsCount());
        assertTrue(rateLimiter.checkLimit(client, 1, 60).isAllowed());
    }
}

