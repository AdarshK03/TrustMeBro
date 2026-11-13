package com.platform.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GatewayTrafficProperties {

    @Value("${security.jwt.enabled:true}")
    private boolean securityJwtEnabled;

    @Value("${security.jwt.secret:${JWT_SECRET:cloud_platform_api_gateway_super_secure_jwt_signing_key_2026_min_256_bits!}}")
    private String jwtSecret;

    @Value("${security.jwt.expiration-ms:${JWT_EXPIRATION_MS:86400000}}")
    private long jwtExpirationMs;

    @Value("${gateway.rate-limit.enabled:false}")
    private boolean rateLimitEnabled;

    @Value("${gateway.rate-limit.max-requests:100}")
    private int rateLimitMaxRequests;

    @Value("${gateway.rate-limit.window-seconds:60}")
    private long rateLimitWindowSeconds;

    @Value("${gateway.cache.enabled:false}")
    private boolean cacheEnabled;

    @Value("${gateway.cache.expiration-seconds:60}")
    private long cacheExpirationSeconds;

    @Value("${gateway.cache.max-size:100}")
    private int cacheMaxSize;

    public boolean isSecurityJwtEnabled() {
        return securityJwtEnabled;
    }

    public void setSecurityJwtEnabled(boolean securityJwtEnabled) {
        this.securityJwtEnabled = securityJwtEnabled;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtExpirationMs() {
        return jwtExpirationMs;
    }

    public void setJwtExpirationMs(long jwtExpirationMs) {
        this.jwtExpirationMs = jwtExpirationMs;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    public int getRateLimitMaxRequests() {
        return rateLimitMaxRequests;
    }

    public void setRateLimitMaxRequests(int rateLimitMaxRequests) {
        this.rateLimitMaxRequests = rateLimitMaxRequests;
    }

    public long getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public void setRateLimitWindowSeconds(long rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public long getCacheExpirationSeconds() {
        return cacheExpirationSeconds;
    }

    public void setCacheExpirationSeconds(long cacheExpirationSeconds) {
        this.cacheExpirationSeconds = cacheExpirationSeconds;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }
}

