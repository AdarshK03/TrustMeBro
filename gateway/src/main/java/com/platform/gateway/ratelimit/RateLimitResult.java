package com.platform.gateway.ratelimit;

public class RateLimitResult {

    private final boolean allowed;
    private final int limit;
    private final int remaining;
    private final long retryAfterSeconds;

    public RateLimitResult(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
        this.allowed = allowed;
        this.limit = limit;
        this.remaining = Math.max(0, remaining);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public static RateLimitResult allow(int limit, int remaining) {
        return new RateLimitResult(true, limit, remaining, 0);
    }

    public static RateLimitResult reject(int limit, long retryAfterSeconds) {
        return new RateLimitResult(false, limit, 0, retryAfterSeconds);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public int getLimit() {
        return limit;
    }

    public int getRemaining() {
        return remaining;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

