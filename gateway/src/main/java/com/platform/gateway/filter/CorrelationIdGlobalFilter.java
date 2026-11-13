package com.platform.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * GlobalFilter that inspects incoming requests for an X-Correlation-ID (or X-Request-ID) header.
 * Validates and sanitizes incoming values against log injection attacks.
 * If absent or invalid, generates a UUID, attaches it to the exchange attributes,
 * propagates it to downstream microservices, and sets it on the response header.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String CORRELATION_ID_ATTR = "GATEWAY_CORRELATION_ID";

    // Allow alphanumeric characters, dashes, underscores, and dots between 1 and 64 characters
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{1,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();

        String rawCorrelationId = headers.getFirst(CORRELATION_ID_HEADER);
        if (rawCorrelationId == null || rawCorrelationId.isBlank()) {
            rawCorrelationId = headers.getFirst(REQUEST_ID_HEADER);
        }

        String correlationId;
        if (rawCorrelationId != null && SAFE_ID_PATTERN.matcher(rawCorrelationId.trim()).matches()) {
            correlationId = rawCorrelationId.trim();
            log.debug("Propagating valid incoming Correlation-ID: {} for {} {}", 
                    correlationId, request.getMethod(), request.getURI().getPath());
        } else {
            correlationId = UUID.randomUUID().toString();
            if (rawCorrelationId != null && !rawCorrelationId.isBlank()) {
                log.warn("Sanitized invalid Correlation-ID: replaced with generated UUID: {}", correlationId);
            } else {
                log.debug("Generated new Correlation-ID: {} for {} {}", 
                        correlationId, request.getMethod(), request.getURI().getPath());
            }
        }

        // Store in exchange attributes for downstream filters and error handlers
        exchange.getAttributes().put(CORRELATION_ID_ATTR, correlationId);

        // Decorate downstream request with Correlation ID and Request ID headers
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .header(REQUEST_ID_HEADER, correlationId)
                .build();

        final String finalCorrelationId = correlationId;

        // Ensure response header contains Correlation ID
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, finalCorrelationId);
            exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, finalCorrelationId);
            return Mono.empty();
        });

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
