package com.platform.gateway.ratelimit;

import com.platform.gateway.config.GatewayTrafficProperties;
import com.platform.gateway.filter.CorrelationIdGlobalFilter;
import com.platform.gateway.filter.RateLimitingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitingFilterTest {

    private GatewayTrafficProperties properties;
    private InMemoryRateLimiter rateLimiter;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        properties = new GatewayTrafficProperties();
        properties.setRateLimitEnabled(true);
        properties.setRateLimitMaxRequests(2);
        properties.setRateLimitWindowSeconds(60);

        rateLimiter = new InMemoryRateLimiter();
        filter = new RateLimitingFilter(properties, rateLimiter);
    }

    private GatewayFilterChain passThroughChain(AtomicBoolean downstreamInvoked) {
        return exchange -> {
            downstreamInvoked.set(true);
            return Mono.empty();
        };
    }

    @Test
    void rateLimitingDisabled_PassesAllRequests() {
        properties.setRateLimitEnabled(false);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products")
                .header("X-Forwarded-For", "192.168.1.1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean invoked = new AtomicBoolean(false);

        for (int i = 0; i < 10; i++) {
            invoked.set(false);
            filter.filter(exchange, passThroughChain(invoked)).block();
            assertTrue(invoked.get(), "When disabled, requests must never be rejected");
        }
    }

    @Test
    void rateLimitingEnabled_ExceedingLimitReturns429WithHeaders() {
        String clientIp = "192.168.5.5";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products")
                .header("X-Forwarded-For", clientIp)
                .build();

        AtomicBoolean invoked = new AtomicBoolean(false);

        // 1st request -> OK
        MockServerWebExchange ex1 = MockServerWebExchange.from(request);
        filter.filter(ex1, passThroughChain(invoked)).block();
        assertTrue(invoked.get());

        // 2nd request -> OK
        invoked.set(false);
        MockServerWebExchange ex2 = MockServerWebExchange.from(request);
        filter.filter(ex2, passThroughChain(invoked)).block();
        assertTrue(invoked.get());

        // 3rd request -> 429 Too Many Requests
        invoked.set(false);
        MockServerWebExchange ex3 = MockServerWebExchange.from(request);
        ex3.getAttributes().put(CorrelationIdGlobalFilter.CORRELATION_ID_ATTR, "corr-rate-limit-429");
        filter.filter(ex3, passThroughChain(invoked)).block();

        assertFalse(invoked.get(), "Downstream must NOT be called when rate limit is exceeded");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex3.getResponse().getStatusCode());
        assertEquals("corr-rate-limit-429", ex3.getResponse().getHeaders().getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER));
        assertNotNull(ex3.getResponse().getHeaders().getFirst("Retry-After"));
        assertEquals("0", ex3.getResponse().getHeaders().getFirst(RateLimitingFilter.X_RATELIMIT_REMAINING));
    }

    @Test
    void actuatorEndpoints_BypassRateLimiter() {
        String clientIp = "192.168.6.6";
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health")
                .header("X-Forwarded-For", clientIp)
                .build();

        AtomicBoolean invoked = new AtomicBoolean(false);

        // Send 10 requests which exceeds limit of 2, but actuator should never be blocked
        for (int i = 0; i < 10; i++) {
            invoked.set(false);
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            filter.filter(exchange, passThroughChain(invoked)).block();
            assertTrue(invoked.get(), "Actuator health must bypass rate limiting");
        }
    }
}

