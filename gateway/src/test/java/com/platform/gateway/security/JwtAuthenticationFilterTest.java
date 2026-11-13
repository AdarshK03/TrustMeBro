package com.platform.gateway.security;

import com.platform.gateway.config.GatewayTrafficProperties;
import com.platform.gateway.filter.CorrelationIdGlobalFilter;
import com.platform.gateway.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {

    private GatewayTrafficProperties properties;
    private JwtValidator jwtValidator;
    private JwtAuthenticationFilter authFilter;

    @BeforeEach
    void setUp() {
        properties = new GatewayTrafficProperties();
        properties.setSecurityJwtEnabled(true);
        properties.setJwtSecret(TestTokenHelper.TEST_SECRET);
        jwtValidator = new JwtValidator(properties);
        authFilter = new JwtAuthenticationFilter(properties, jwtValidator);
    }

    private GatewayFilterChain passThroughChain(AtomicBoolean downstreamInvoked) {
        return exchange -> {
            downstreamInvoked.set(true);
            return Mono.empty();
        };
    }

    @Test
    void publicRoute_Login_PassesWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/users/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        authFilter.filter(exchange, passThroughChain(downstreamCalled)).block();

        assertTrue(downstreamCalled.get(), "Downstream should be invoked for public login");
        assertNotEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void publicRoute_ProductCatalogGet_PassesWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/products/42").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        authFilter.filter(exchange, passThroughChain(downstreamCalled)).block();

        assertTrue(downstreamCalled.get(), "Public GET /api/products should not require token");
    }

    @Test
    void publicRoute_ActuatorHealth_PassesWithoutToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        authFilter.filter(exchange, passThroughChain(downstreamCalled)).block();

        assertTrue(downstreamCalled.get(), "Actuator health should not require token");
    }

    @Test
    void protectedRoute_MissingToken_Returns401Unauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CorrelationIdGlobalFilter.CORRELATION_ID_ATTR, "test-corr-missing");
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        authFilter.filter(exchange, passThroughChain(downstreamCalled)).block();

        assertFalse(downstreamCalled.get(), "Downstream must NOT be invoked when token is missing");
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void protectedRoute_ValidToken_PassesAndPropagatesUserHeaders() {
        String token = TestTokenHelper.generateValidToken(101L, "bob@example.com", "CUSTOMER");
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        GatewayFilterChain captureChain = ex -> {
            downstreamCalled.set(true);
            assertEquals("101", ex.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.USER_ID_HEADER));
            assertEquals("bob@example.com", ex.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.USER_EMAIL_HEADER));
            assertEquals("CUSTOMER", ex.getRequest().getHeaders().getFirst(JwtAuthenticationFilter.USER_ROLE_HEADER));
            return Mono.empty();
        };

        authFilter.filter(exchange, captureChain).block();

        assertTrue(downstreamCalled.get(), "Downstream must be invoked for valid token");
    }

    @Test
    void protectedRoute_ExpiredToken_Returns401Unauthorized() {
        String expiredToken = TestTokenHelper.generateExpiredToken(102L, "expired@example.com", "CUSTOMER");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/5")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        authFilter.filter(exchange, passThroughChain(downstreamCalled)).block();

        assertFalse(downstreamCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void protectedRoute_TamperedToken_Returns401Unauthorized() {
        String validToken = TestTokenHelper.generateValidToken(103L, "tampered@example.com", "CUSTOMER");
        String tamperedToken = validToken.substring(0, validToken.length() - 6) + "xxxxxx";

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/orders/5")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        authFilter.filter(exchange, passThroughChain(downstreamCalled)).block();

        assertFalse(downstreamCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void roleAuthorization_CustomerAttemptingProductWrite_Returns403Forbidden() {
        String customerToken = TestTokenHelper.generateValidToken(201L, "cust@example.com", "CUSTOMER");
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        authFilter.filter(exchange, passThroughChain(downstreamCalled)).block();

        assertFalse(downstreamCalled.get(), "Downstream must not be invoked for forbidden role");
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void roleAuthorization_AdminAttemptingProductWrite_Passes() {
        String adminToken = TestTokenHelper.generateValidToken(1L, "admin@example.com", "ADMIN");
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        authFilter.filter(exchange, passThroughChain(downstreamCalled)).block();

        assertTrue(downstreamCalled.get(), "Downstream must be invoked for admin writing products");
    }

    @Test
    void securityDisabled_BypassesAuthentication() {
        properties.setSecurityJwtEnabled(false);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean downstreamCalled = new AtomicBoolean(false);

        authFilter.filter(exchange, passThroughChain(downstreamCalled)).block();

        assertTrue(downstreamCalled.get(), "When security is disabled, requests bypass token checks");
    }
}

