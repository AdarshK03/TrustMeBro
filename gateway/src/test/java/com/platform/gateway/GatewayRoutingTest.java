package com.platform.gateway;

import com.platform.gateway.filter.CorrelationIdGlobalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "USER_SERVICE_URL=http://localhost:59991",
        "PRODUCT_SERVICE_URL=http://localhost:59992",
        "ORDER_SERVICE_URL=http://localhost:59993"
})
class GatewayRoutingTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteLocator routeLocator;

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Test
    void routeDefinitions_ExistForAllMicroservices() {
        List<String> routeIds = routeLocator.getRoutes()
                .map(r -> r.getId())
                .collectList()
                .block();

        assertNotNull(routeIds);
        assertTrue(routeIds.contains("user-service-route"), "user-service-route must be defined");
        assertTrue(routeIds.contains("product-service-route"), "product-service-route must be defined");
        assertTrue(routeIds.contains("order-service-route"), "order-service-route must be defined");
    }

    @Test
    void correlationId_GeneratedWhenAbsent() {
        webTestClient.get()
                .uri("/api/products/1")
                .exchange()
                .expectHeader().exists(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER)
                .expectHeader().value(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, value ->
                        assertTrue(UUID_PATTERN.matcher(value).matches(), "Generated ID must be a valid UUID"));
    }

    @Test
    void correlationId_PropagatedWhenValid() {
        String inputId = "client-trace-abc-123";

        webTestClient.get()
                .uri("/api/users/1")
                .header(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, inputId)
                .exchange()
                .expectHeader().valueEquals(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, inputId);
    }

    @Test
    void correlationId_SanitizedWhenInvalidOrLogInjection() {
        String dangerousId = "bad\r\nSet-Cookie: malicious=true";

        webTestClient.get()
                .uri("/api/orders/1")
                .header(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, dangerousId)
                .exchange()
                .expectHeader().value(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, value -> {
                    assertNotEquals(dangerousId, value, "Malformed ID must not be echoed back");
                    assertTrue(UUID_PATTERN.matcher(value).matches(), "Sanitized ID should be a safe UUID");
                });
    }

    @Test
    void downstreamUnavailable_Returns503ServiceUnavailableWithConsistentErrorPayload() {
        webTestClient.get()
                .uri("/api/products/99")
                .header(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, "test-503-trace")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().valueEquals(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, "test-503-trace")
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("Service Unavailable")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.path").isEqualTo("/api/products/99")
                .jsonPath("$.requestId").isEqualTo("test-503-trace")
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    void actuatorHealth_IsAvailableAndReportsUp() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}

